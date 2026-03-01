// 玩家ESP网络通信管理器
// 负责处理与服务器的WebSocket连接和数据同步
package fun.prof_chen.teamviewer.multipleplayeresp.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fun.prof_chen.teamviewer.multipleplayeresp.config.Config;
import fun.prof_chen.teamviewer.multipleplayeresp.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.multipleplayeresp.model.SharedWaypointInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.Proxy;
import java.net.ProxySelector;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * PlayerESP 网络层管理器 - 核心网络通信组件
 * 
 * ## 核心功能
 * 1) WebSocket连接管理：建立、维护、重连、断开连接
 * 2) 数据双向同步：玩家位置、实体信息、路标数据的上传下载
 * 3) 增量更新机制：支持差分同步，减少网络流量
 * 4) 数据一致性保障：通过摘要校验和强制刷新机制确保数据同步
 * 
 * ## 协议特性
 * - 支持版本协商和协议兼容性检测
 * - 实现心跳保活和自动重连机制
 * - 提供完整的错误处理和状态监控
 * 
 * ## 线程安全设计
 * - 网络回调在OkHttp工作线程执行
 * - 状态变更通过任务队列串行化到Minecraft主线程
 * - 避免跨线程直接修改共享数据结构
 */
// 网络管理器主类，继承WebSocketListener处理网络事件
public class PlayerESPNetworkManager extends WebSocketListener {
	/**
	 * 连接状态监听器接口
	 * 用于通知UI和其他模块网络连接状态变化
	 */
	public interface ConnectionStatusListener {
		void onConnectionStatusChanged(boolean connected);
	}

	/**
	 * 路标更新监听器接口
	 * 处理远程路标数据的接收和删除事件
	 */
	public interface WaypointUpdateListener {
		/**
		 * 当接收到新的路标数据时调用
		 * @param waypoints 新增或更新的路标映射
		 */
		void onWaypointsReceived(Map<String, SharedWaypointInfo> waypoints);

		/**
		 * 当路标被删除时调用
		 * @param waypointIds 被删除的路标ID列表
		 */
		default void onWaypointsDeleted(List<String> waypointIds) {
		}
	}

	// 日志记录器
	private static final Logger LOGGER = LoggerFactory.getLogger(PlayerESPNetworkManager.class);
	
	// 客户端协议版本号 - 用于服务端兼容性检查
	private static final String CLIENT_PROTOCOL_VERSION = "0.2.0";
	
	// 客户端程序版本 - 从Mod元数据中获取
	private static final String CLIENT_PROGRAM_VERSION = resolveLocalProgramVersion();
	
	// 重同步冷却时间(毫秒) - 防止频繁重同步请求
	private static final long RESYNC_COOLDOWN_MS = 3_000L;
	
	// 强制全量刷新间隔(毫秒) - 确保数据最终一致性
	private static final long FORCE_FULL_REFRESH_MS = 25_000L;

	// 全局配置引用
	private static Config config;

	// 本地玩家位置缓存 - 用于快速查找玩家坐标
	private final Map<UUID, Vec3d> playerPositions;
	
	// 远程玩家信息缓存 - 存储其他客户端玩家的位置和维度信息
	private final Map<UUID, RemotePlayerInfo> remotePlayers;
	
	// 远程玩家数据缓存 - 存储玩家的完整属性数据
	private final Map<UUID, Map<String, Object>> remotePlayerDataCache = new HashMap<>();
	
	// 远程实体数据缓存 - 存储世界中实体的位置和属性
	private final Map<String, Map<String, Object>> remoteEntityDataCache = new HashMap<>();
	
	// 远程路标原始数据缓存 - 存储路标的完整数据结构
	private final Map<String, Map<String, Object>> remoteWaypointDataCache = new HashMap<>();
	
	// 远程路标对象缓存 - 存储解析后的SharedWaypointInfo对象
	private final Map<String, SharedWaypointInfo> remoteWaypointCache = new HashMap<>();
	
	// 玩家标记状态缓存 - 存储玩家的队伍归属和颜色标记
	private final Map<String, PlayerMarkState> remotePlayerMarks = new HashMap<>();
	
	// 上次发送的玩家快照 - 用于计算增量更新
	private final Map<String, Map<String, Object>> lastSentPlayersSnapshot = new HashMap<>();
	
	// 上次发送的实体快照 - 用于计算增量更新
	private final Map<String, Map<String, Object>> lastSentEntitiesSnapshot = new HashMap<>();

	// WebSocket连接实例
	private WebSocket webSocket;
	
	// 重连调度器 - 负责连接失败后的自动重连
	private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
	
	// 连接状态标志 - 表示当前是否与服务器保持连接
	private volatile boolean isConnected = false;
	
	// 重连意愿标志 - 控制是否应该尝试重连
	private volatile boolean shouldReconnect = false;
	
	// JSON序列化工具 - 用于协议数据的编码解码
	private final Gson gson = new Gson();
	
	// HTTP客户端 - 用于创建WebSocket连接
	private OkHttpClient httpClient;
	
	// 代理设置状态 - 记录当前是否使用系统代理
	private volatile boolean currentUseSystemProxy = true;

	// 连接状态监听器列表 - 线程安全的监听器注册表
	private final List<ConnectionStatusListener> statusListeners = new CopyOnWriteArrayList<>();
	
	// 路标更新监听器列表 - 线程安全的监听器注册表
	private final List<WaypointUpdateListener> waypointListeners = new CopyOnWriteArrayList<>();

	// 最近一次连接错误信息 - 用于诊断连接问题
	private volatile String lastConnectionError = "";
	
	// 服务端是否支持增量更新 - 影响数据传输策略
	private volatile boolean serverSupportsDelta = false;
	
	// 服务端协议版本 - 用于版本兼容性判断
	private volatile String serverProtocolVersion = "0.0.0";
	
	// 服务端程序版本 - 用于版本对比和调试
	private volatile String serverProgramVersion = "unknown";
	
	// 摘要校验间隔(秒) - 控制数据一致性检查频率
	private volatile int digestIntervalSec = 10;
	
	// 服务端最新修订版本号 - 用于增量同步
	private volatile long lastServerRevision = 0;
	
	// 上次重同步请求时间戳 - 防止重复请求
	private volatile long lastResyncRequestMs = 0L;
	
	// 上次发送玩家数据包的时间 - 用于强制刷新判断
	private volatile long lastPlayersPacketSentMs = 0L;
	
	// 上次发送实体数据包的时间 - 用于强制刷新判断
	private volatile long lastEntitiesPacketSentMs = 0L;
	
	// 上次发送Tab玩家列表的时间 - 用于去重优化
	private volatile long lastTabPlayersPacketSentMs = 0L;
	
	// 上次发送Tab玩家列表的签名 - 用于内容变化检测
	private volatile String lastTabPlayersSignature = "";
	
	// 待刷新的玩家ID集合 - 响应服务端刷新请求
	private final Set<String> pendingPlayerRefreshIds = new HashSet<>();
	
	// 待刷新的实体ID集合 - 响应服务端刷新请求
	private final Set<String> pendingEntityRefreshIds = new HashSet<>();
	/**
	 * 主线程任务队列 - 线程安全的任务传递机制
	 * 
	 * 设计原理：
	 * - WebSocket回调在线程池中异步执行
	 * - 通过队列将任务传递给Minecraft主线程
	 * - 在客户端tick循环中顺序处理这些任务
	 * 
	 * 优势：
	 * - 避免跨线程直接修改共享数据结构
	 * - 确保所有状态变更在同一线程中执行
	 * - 防止并发修改异常和数据不一致
	 */
	private final Queue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();

	/**
	 * 玩家标记状态记录类
	 * 存储玩家的队伍归属、颜色标记和自定义标签
	 */
	private record PlayerMarkState(String team, Integer color, String label) {
	}

	/**
	 * 构造函数
	 * @param playerPositions 本地玩家位置映射的引用
	 * @param remotePlayers 远程玩家信息映射的引用
	 */
	public PlayerESPNetworkManager(Map<UUID, Vec3d> playerPositions, Map<UUID, RemotePlayerInfo> remotePlayers) {
		this.playerPositions = playerPositions;
		this.remotePlayers = remotePlayers;
		this.httpClient = createHttpClient(true); // 默认启用系统代理
	}

	/**
	 * 设置全局配置实例
	 * @param config 配置对象引用
	 */
	public static void setConfig(Config config) {
		PlayerESPNetworkManager.config = config;
	}

	/**
	 * 处理主线程任务队列 - 核心线程同步机制
	 * 
	 * 执行时机：在StandaloneMultiPlayerESP的END_CLIENT_TICK事件中调用
	 * 功能说明：
	 * - 顺序执行所有排队的网络任务
	 * - 将异步网络回调的结果应用到主线程状态
	 * - 确保对共享数据结构的操作是线程安全的
	 * 
	 * 异常处理：捕获并记录任务执行中的错误，防止队列处理中断
	 */
	public void pumpMainThreadTasks() {
		Runnable task;
		while ((task = mainThreadTasks.poll()) != null) {
			try {
				task.run();
			} catch (Exception e) {
				LOGGER.error("Error while processing queued network task: {}", e.getMessage());
			}
		}
	}

	/**
	 * 将任务加入主线程执行队列
	 * 
	 * 使用场景：
	 * - WebSocket事件回调中需要修改共享状态时
	 * - 网络数据解析完成后需要更新UI时
	 * - 需要确保在主线程执行的任何操作
	 * 
	 * 线程安全：使用ConcurrentLinkedQueue保证多线程环境下的安全入队
	 */
	private void enqueueMainThreadTask(Runnable task) {
		if (task != null) {
			mainThreadTasks.offer(task);
		}
	}

	/**
	 * 建立WebSocket连接 - 网络通信入口点
	 * 
	 * 连接流程：
	 * 1. 检查配置有效性
	 * 2. 设置重连标志
	 * 3. 根据代理配置创建HTTP客户端
	 * 4. 构建WebSocket连接请求
	 * 5. 发起连接并注册回调监听器
	 * 
	 * 错误处理：
	 * - 连接失败时记录错误信息
	 * - 通知监听器连接状态变化
	 * - 自动调度重连机制
	 */
	public void connect() {
		if (config == null) {
			return;
		}
		shouldReconnect = true;

		boolean useSystemProxy = config.isUseSystemProxy();
		if (this.httpClient == null || this.currentUseSystemProxy != useSystemProxy) {
			this.httpClient = createHttpClient(useSystemProxy);
			this.currentUseSystemProxy = useSystemProxy;
		}

		String uri = config.getServerURL();
		Request.Builder builder = new Request.Builder().url(uri);

		try {
			this.webSocket = httpClient.newWebSocket(builder.build(), this);
		} catch (Exception e) {
			this.isConnected = false;
			this.lastConnectionError = formatThrowableReason(e);
			LOGGER.error("Failed to connect to PlayerESP server at {}: {}", config.getServerURL(), e.getMessage());
			notifyConnectionStatusChanged(false);
			scheduleReconnect();
		}
	}

	/**
	 * 创建HTTP客户端实例
	 * 
	 * 代理配置选项：
	 * - useSystemProxy=true: 使用系统默认代理设置
	 * - useSystemProxy=false: 不使用代理直接连接
	 * 
	 * 应用场景：
	 * - 初始连接时根据配置创建客户端
	 * - 代理设置改变时重新创建客户端
	 * - 重连时可能需要更新代理配置
	 */
	private OkHttpClient createHttpClient(boolean useSystemProxy) {
		OkHttpClient.Builder builder = new OkHttpClient.Builder();
		if (useSystemProxy) {
			builder.proxySelector(ProxySelector.getDefault());
		} else {
			builder.proxy(Proxy.NO_PROXY);
		}
		return builder.build();
	}

	/**
	 * 调度自动重连任务
	 * 
	 * 重连策略：
	 * - 延迟5秒后尝试重新连接
	 * - 只在shouldReconnect为true时执行
	 * - 使用单线程调度器避免并发重连
	 * 
	 * 异常处理：
	 * - 捕获调度器不可用的情况
	 * - 记录警告日志但不影响主流程
	 */
	private void scheduleReconnect() {
		if (!shouldReconnect) {
			return;
		}
		try {
			reconnectExecutor.schedule(this::connect, 5, TimeUnit.SECONDS);
		} catch (RejectedExecutionException e) {
			LOGGER.warn("Reconnect scheduler is unavailable: {}", e.getMessage());
		}
	}

	/**
	 * 断开WebSocket连接 - 主动关闭连接
	 * 
	 * 清理流程：
	 * 1. 取消重连意愿
	 * 2. 关闭WebSocket连接(状态码1000表示正常关闭)
	 * 3. 重置协议协商状态
	 * 4. 清空本地快照缓存
	 * 5. 更新连接状态标志
	 * 6. 通知监听器状态变化
	 * 
	 * 注意事项：这是一个干净的关闭过程，不会触发重连机制
	 */
	public void disconnect() {
		shouldReconnect = false;
		if (webSocket != null) {
			webSocket.close(1000, "Client disconnect");
			webSocket = null;
		}
		resetNegotiationState();
		clearLocalOutboundSnapshots();
		isConnected = false;
		lastConnectionError = "";
		notifyConnectionStatusChanged(false);
	}

	/**
	 * 注册连接状态监听器
	 * @param listener 连接状态变化监听器实例
	 */
	public void addConnectionStatusListener(ConnectionStatusListener listener) {
		if (listener != null) {
			statusListeners.add(listener);
		}
	}

	/**
	 * 移除连接状态监听器
	 * @param listener 要移除的监听器实例
	 */
	public void removeConnectionStatusListener(ConnectionStatusListener listener) {
		if (listener != null) {
			statusListeners.remove(listener);
		}
	}

	/**
	 * 注册路标更新监听器
	 * @param listener 路标数据更新监听器实例
	 */
	public void addWaypointUpdateListener(WaypointUpdateListener listener) {
		if (listener != null) {
			waypointListeners.add(listener);
		}
	}

	/**
	 * 移除路标更新监听器
	 * @param listener 要移除的监听器实例
	 */
	public void removeWaypointUpdateListener(WaypointUpdateListener listener) {
		if (listener != null) {
			waypointListeners.remove(listener);
		}
	}

	/**
	 * 发送玩家位置更新数据 - 核心上行数据传输方法
	 * 
	 * 协议选择逻辑：
	 * - 如果服务端支持增量更新：使用players_patch协议发送差分数据
	 * - 如果服务端不支持增量更新：降级使用players_update协议发送全量数据
	 * 
	 * 增量更新算法：
	 * 1. 构建当前玩家状态快照
	 * 2. 与上次发送的快照进行比较
	 * 3. 识别新增、修改、删除的玩家记录
	 * 4. 处理服务端的刷新请求
	 * 5. 只发送发生变化的数据以节省带宽
	 * 
	 * 协议格式(players_patch)：
	 * {
	 *   "type": "players_patch",
	 *   "submitPlayerId": "发送者UUID",
	 *   "ackRev": 服务端修订版本号,
	 *   "upsert": {玩家ID: 变更字段},
	 *   "delete": [要删除的玩家ID列表]
	 * }
	 * 
	 * 性能优化：
	 * - 25秒强制全量刷新防止数据漂移
	 * - 快照机制避免重复发送相同数据
	 * - 批量处理提高网络效率
	 */
	public void sendPlayersUpdate(UUID submitPlayerId, Map<UUID, Map<String, Object>> players) {
		if (webSocket == null || !isConnected || submitPlayerId == null || players == null) {
			return;
		}

		if (!serverSupportsDelta) {
			sendPlayersUpdateLegacy(submitPlayerId, players);
			return;
		}

		Map<String, Map<String, Object>> currentSnapshot = new HashMap<>();
		for (Map.Entry<UUID, Map<String, Object>> entry : players.entrySet()) {
			currentSnapshot.put(entry.getKey().toString(), copyValueMap(entry.getValue()));
		}

		Map<String, Map<String, Object>> upsert = new HashMap<>();
		List<String> delete = new ArrayList<>();
		boolean forceFullRefresh = shouldForcePlayersFullRefresh();

		for (Map.Entry<String, Map<String, Object>> entry : currentSnapshot.entrySet()) {
			Map<String, Object> previous = lastSentPlayersSnapshot.get(entry.getKey());
			if (forceFullRefresh || previous == null) {
				upsert.put(entry.getKey(), entry.getValue());
				continue;
			}

			Map<String, Object> fieldDelta = computeFieldDelta(previous, entry.getValue());
			if (!fieldDelta.isEmpty()) {
				upsert.put(entry.getKey(), fieldDelta);
			}
		}

		for (String previousId : lastSentPlayersSnapshot.keySet()) {
			if (!currentSnapshot.containsKey(previousId)) {
				delete.add(previousId);
			}
		}

		applyPendingPlayerRefresh(currentSnapshot, upsert, delete);

		if (upsert.isEmpty() && delete.isEmpty()) {
			return;
		}

		try {
			long sentAt = System.currentTimeMillis();
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "players_patch");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			obj.addProperty("ackRev", lastServerRevision);
			obj.add("upsert", mapOfMapToJsonObject(upsert));
			obj.add("delete", toStringArray(delete));
			webSocket.send(gson.toJson(obj));
			lastSentPlayersSnapshot.clear();
			lastSentPlayersSnapshot.putAll(currentSnapshot);
			lastPlayersPacketSentMs = sentAt;
		} catch (Exception e) {
			LOGGER.error("Failed to send players_patch: {}", e.getMessage());
		}
	}

	/**
	 * 发送实体位置更新数据 - 实体信息同步方法
	 * 
	 * 功能说明：
	 * 与sendPlayersUpdate类似的增量更新机制，专门处理世界实体数据
	 * 包括怪物、掉落物、载具等非玩家实体的位置和状态信息
	 * 
	 * 协议格式(entities_patch)：
	 * {
	 *   "type": "entities_patch",
	 *   "submitPlayerId": "发送者UUID",
	 *   "ackRev": 服务端修订版本号,
	 *   "upsert": {实体ID: 实体数据变更},
	 *   "delete": [要删除的实体ID列表]
	 * }
	 * 
	 * 优化特点：
	 * - 实体数据通常变化频率较低
	 * - 使用相同的快照和差分算法
	 * - 25秒强制刷新确保数据一致性
	 * - 支持服务端主动刷新请求
	 */
	public void sendEntitiesUpdate(UUID submitPlayerId, Map<String, Map<String, Object>> entities) {
		if (webSocket == null || !isConnected || submitPlayerId == null || entities == null) {
			return;
		}

		if (!serverSupportsDelta) {
			sendEntitiesUpdateLegacy(submitPlayerId, entities);
			return;
		}

		Map<String, Map<String, Object>> currentSnapshot = new HashMap<>();
		for (Map.Entry<String, Map<String, Object>> entry : entities.entrySet()) {
			currentSnapshot.put(entry.getKey(), copyValueMap(entry.getValue()));
		}

		Map<String, Map<String, Object>> upsert = new HashMap<>();
		List<String> delete = new ArrayList<>();
		boolean forceFullRefresh = shouldForceEntitiesFullRefresh();

		for (Map.Entry<String, Map<String, Object>> entry : currentSnapshot.entrySet()) {
			Map<String, Object> previous = lastSentEntitiesSnapshot.get(entry.getKey());
			if (forceFullRefresh || previous == null) {
				upsert.put(entry.getKey(), entry.getValue());
				continue;
			}

			Map<String, Object> fieldDelta = computeFieldDelta(previous, entry.getValue());
			if (!fieldDelta.isEmpty()) {
				upsert.put(entry.getKey(), fieldDelta);
			}
		}

		for (String previousId : lastSentEntitiesSnapshot.keySet()) {
			if (!currentSnapshot.containsKey(previousId)) {
				delete.add(previousId);
			}
		}

		applyPendingEntityRefresh(currentSnapshot, upsert, delete);

		if (upsert.isEmpty() && delete.isEmpty()) {
			return;
		}

		try {
			long sentAt = System.currentTimeMillis();
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "entities_patch");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			obj.addProperty("ackRev", lastServerRevision);
			obj.add("upsert", mapOfMapToJsonObject(upsert));
			obj.add("delete", toStringArray(delete));
			webSocket.send(gson.toJson(obj));
			lastSentEntitiesSnapshot.clear();
			lastSentEntitiesSnapshot.putAll(currentSnapshot);
			lastEntitiesPacketSentMs = sentAt;
		} catch (Exception e) {
			LOGGER.error("Failed to send entities_patch: {}", e.getMessage());
		}
	}

	/**
	 * 发送路标更新数据 - 路标信息上传方法
	 * 
	 * 功能说明：
	 * 上传本地创建或修改的路标信息到服务器
	 * 路标包括坐标点、名称、颜色、所属玩家等信息
	 * 
	 * 协议格式(waypoints_update)：
	 * {
	 *   "type": "waypoints_update",
	 *   "submitPlayerId": "发送者UUID",
	 *   "waypoints": {
	 *     "路标ID": {
	 *       "x": 坐标X,
	 *       "y": 坐标Y,
	 *       "z": 坐标Z,
	 *       "name": "路标名称",
	 *       "color": 颜色值,
	 *       ...
	 *     }
	 *   }
	 * }
	 * 
	 * 特点：
	 * - 目前采用全量更新方式(未来可优化为增量更新)
	 * - 每个路标都有唯一ID标识
	 * - 支持多种路标类型和属性
	 */
	public void sendWaypointsUpdate(UUID submitPlayerId, Map<String, Map<String, Object>> waypoints) {
		if (webSocket == null || !isConnected)
			return;
		if (waypoints == null || waypoints.isEmpty())
			return;
		try {
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "waypoints_update");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			obj.add("waypoints", mapOfMapToJsonObject(waypoints));
			webSocket.send(gson.toJson(obj));
		} catch (Exception e) {
			LOGGER.error("Failed to send waypoints_update to PlayerESP server: {}", e.getMessage());
		}
	}

	/**
	 * 发送Tab玩家列表更新 - 玩家列表信息同步
	 * 
	 * 功能说明：
	 * 同步当前可见玩家的列表信息，包括UUID、名称、显示名等
	 * 主要用于玩家标记和队伍识别功能
	 * 
	 * 优化策略：
	 * - 内容签名去重：相同内容不重复发送
	 * - 时间间隔控制：避免过于频繁的更新
	 * - 数据标准化：提取必要的字段信息
	 * 
	 * 协议格式(tab_players_update)：
	 * {
	 *   "type": "tab_players_update",
	 *   "submitPlayerId": "发送者UUID",
	 *   "ackRev": 服务端修订版本号,
	 *   "tabPlayers": [
	 *     {
	 *       "id": "玩家UUID",
	 *       "name": "玩家名",
	 *       "displayName": "显示名称"
	 *     }
	 *   ]
	 * }
	 * 
	 * 性能考虑：
	 * - 25秒强制刷新确保数据新鲜度
	 * - JSON签名比较避免网络浪费
	 * - 只传输必要字段减少数据量
	 */
	public void sendTabPlayersUpdate(UUID submitPlayerId, List<Map<String, Object>> tabPlayers) {
		if (webSocket == null || !isConnected || submitPlayerId == null || tabPlayers == null) {
			return;
		}

		try {
			List<Map<String, Object>> normalized = new ArrayList<>();
			for (Map<String, Object> raw : tabPlayers) {
				if (raw == null || raw.isEmpty()) {
					continue;
				}
				Map<String, Object> copy = new HashMap<>();
				Object idValue = raw.get("playerUUID");
				Object nameValue = raw.get("name");
				Object displayName = raw.get("prefixColored");

				if (idValue != null && !String.valueOf(idValue).isBlank()) {
					copy.put("id", String.valueOf(idValue));
				}
				if (nameValue != null && !String.valueOf(nameValue).isBlank()) {
					copy.put("name", String.valueOf(nameValue));
				}
				if (displayName != null && !String.valueOf(displayName).isBlank()) {
					copy.put("displayName", String.valueOf(displayName));
				}

				if (!copy.isEmpty()) {
					normalized.add(copy);
				}
			}

			String signature = gson.toJson(normalized);
			long now = System.currentTimeMillis();
			if (Objects.equals(signature, lastTabPlayersSignature)
					&& now - lastTabPlayersPacketSentMs < FORCE_FULL_REFRESH_MS) {
				return;
			}

			JsonObject obj = new JsonObject();
			obj.addProperty("type", "tab_players_update");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			obj.addProperty("ackRev", lastServerRevision);
			obj.add("tabPlayers", gson.toJsonTree(normalized));
			webSocket.send(gson.toJson(obj));

			lastTabPlayersSignature = signature;
			lastTabPlayersPacketSentMs = now;
		} catch (Exception e) {
			LOGGER.error("Failed to send tab_players_update: {}", e.getMessage());
		}
	}

	/**
	 * 发送路标删除请求 - 路标移除通知
	 * 
	 * 功能说明：
	 * 通知服务器删除指定的路标记录
	 * 通常在玩家删除本地路标时调用
	 * 
	 * 协议格式(waypoints_delete)：
	 * {
	 *   "type": "waypoints_delete",
	 *   "submitPlayerId": "发送者UUID",
	 *   "waypointIds": ["路标ID1", "路标ID2", ...]
	 * }
	 * 
	 * 数据验证：
	 * - 过滤空值和空白字符串
	 * - 确保至少有一个有效ID才发送
	 * - 批量删除提高效率
	 */
	public void sendWaypointsDelete(UUID submitPlayerId, List<String> waypointIds) {
		if (webSocket == null || !isConnected)
			return;
		if (waypointIds == null || waypointIds.isEmpty())
			return;
		try {
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "waypoints_delete");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			JsonArray ids = new JsonArray();
			for (String waypointId : waypointIds) {
				if (waypointId != null && !waypointId.isBlank()) {
					ids.add(waypointId);
				}
			}
			if (ids.isEmpty()) {
				return;
			}
			obj.add("waypointIds", ids);
			webSocket.send(gson.toJson(obj));
		} catch (Exception e) {
			LOGGER.error("Failed to send waypoints_delete to PlayerESP server: {}", e.getMessage());
		}
	}

	/**
	 * 发送实体死亡取消请求 - 路标关联实体保护
	 * 
	 * 功能说明：
	 * 当路标关联的实体即将死亡时，发送此请求阻止路标自动删除
	 * 用于保护重要的目标实体路标
	 * 
	 * 应用场景：
	 * - 玩家设置了追踪特定实体的路标
	 * - 该实体受到攻击但玩家希望保留路标
	 * - 防止因实体死亡导致的路标意外删除
	 * 
	 * 协议格式(waypoints_entity_death_cancel)：
	 * {
	 *   "type": "waypoints_entity_death_cancel",
	 *   "submitPlayerId": "发送者UUID",
	 *   "targetEntityIds": ["实体ID1", "实体ID2", ...]
	 * }
	 */
	public void sendWaypointEntityDeathCancel(UUID submitPlayerId, List<String> targetEntityIds) {
		if (webSocket == null || !isConnected)
			return;
		if (submitPlayerId == null || targetEntityIds == null || targetEntityIds.isEmpty())
			return;
		try {
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "waypoints_entity_death_cancel");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			JsonArray ids = new JsonArray();
			for (String entityId : targetEntityIds) {
				if (entityId != null && !entityId.isBlank()) {
					ids.add(entityId);
				}
			}
			if (ids.isEmpty()) {
				return;
			}
			obj.add("targetEntityIds", ids);
			webSocket.send(gson.toJson(obj));
		} catch (Exception e) {
			LOGGER.error("Failed to send waypoints_entity_death_cancel to PlayerESP server: {}", e.getMessage());
		}
	}

	private void sendPlayersUpdateLegacy(UUID submitPlayerId, Map<UUID, Map<String, Object>> players) {
		if (players.isEmpty()) {
			return;
		}
		try {
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "players_update");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			JsonObject playersJson = new JsonObject();
			for (Map.Entry<UUID, Map<String, Object>> e : players.entrySet()) {
				playersJson.add(e.getKey().toString(), mapToJsonObject(e.getValue()));
			}
			obj.add("players", playersJson);
			webSocket.send(gson.toJson(obj));
		} catch (Exception e) {
			LOGGER.error("Failed to send players_update to PlayerESP server: {}", e.getMessage());
		}
	}

	private void sendEntitiesUpdateLegacy(UUID submitPlayerId, Map<String, Map<String, Object>> entities) {
		if (entities.isEmpty()) {
			return;
		}
		try {
			JsonObject obj = new JsonObject();
			obj.addProperty("type", "entities_update");
			obj.addProperty("submitPlayerId", submitPlayerId.toString());
			JsonObject entitiesJson = new JsonObject();
			for (Map.Entry<String, Map<String, Object>> e : entities.entrySet()) {
				entitiesJson.add(e.getKey(), mapToJsonObject(e.getValue()));
			}
			obj.add("entities", entitiesJson);
			webSocket.send(gson.toJson(obj));
		} catch (Exception e) {
			LOGGER.error("Failed to send entities_update to PlayerESP server: {}", e.getMessage());
		}
	}

	private JsonObject mapToJsonObject(Map<String, Object> map) {
		JsonObject object = new JsonObject();
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			if (entry.getValue() == null) {
				object.add(entry.getKey(), JsonNull.INSTANCE);
			} else {
				object.add(entry.getKey(), gson.toJsonTree(entry.getValue()));
			}
		}
		return object;
	}

	private JsonObject mapOfMapToJsonObject(Map<String, Map<String, Object>> map) {
		JsonObject object = new JsonObject();
		for (Map.Entry<String, Map<String, Object>> entry : map.entrySet()) {
			object.add(entry.getKey(), mapToJsonObject(entry.getValue()));
		}
		return object;
	}

	private JsonArray toStringArray(List<String> list) {
		JsonArray array = new JsonArray();
		for (String value : list) {
			if (value != null && !value.isBlank()) {
				array.add(value);
			}
		}
		return array;
	}

	/**
	 * WebSocket连接成功回调 - 协议握手启动点
	 * 
	 * 执行流程：
	 * 1. 通过任务队列切换到主线程执行
	 * 2. 更新连接状态标志
	 * 3. 清理之前的协商状态和快照数据
	 * 4. 记录连接成功日志
	 * 5. 检查WebSocket扩展支持情况
	 * 6. 通知所有监听器连接已建立
	 * 7. 发送握手消息开始协议协商
	 * 
	 * 线程安全：
	 * - 使用enqueueMainThreadTask确保状态变更在主线程执行
	 * - 避免在回调线程直接修改共享数据结构
	 */
	@Override
	public void onOpen(WebSocket webSocket, Response response) {
		// 连接建立事件来自 OkHttp 线程，这里只投递任务，避免直接跨线程改共享状态。
		enqueueMainThreadTask(() -> {
			isConnected = true;
			lastConnectionError = "";
			resetNegotiationState();
			clearLocalOutboundSnapshots();
			LOGGER.info("WebSocket connection opened to PlayerESP server");
			if (response != null) {
				String negotiatedExtensions = response.header("Sec-WebSocket-Extensions", "");
				if (!negotiatedExtensions.isBlank()) {
					LOGGER.info("Negotiated WebSocket extensions: {}", negotiatedExtensions);
				}
			}
			notifyConnectionStatusChanged(true);
			sendHandshake();
		});
	}

	/**
	 * WebSocket消息接收回调 - 下行数据处理入口
	 * 
	 * 处理逻辑：
	 * 1. 接收服务端发送的JSON文本消息
	 * 2. 通过任务队列转发到主线程处理
	 * 3. 调用processCompleteMessage进行完整的消息解析
	 * 
	 * 设计原则：
	 * - 所有消息处理都在主线程执行
	 * - 保证与游戏渲染循环的一致性
	 * - 避免并发访问共享数据结构
	 */
	@Override
	public void onMessage(WebSocket webSocket, String text) {
		// 消息解析与缓存更新在主线程执行，确保与渲染/tick 读写同线程。
		enqueueMainThreadTask(() -> processCompleteMessage(text));
	}

	/**
	 * 处理服务端完整消息 - 核心下行消息分发器
	 * 
	 * 消息类型分类处理：
	 * 
	 * 1. 握手确认(handshake_ack)：
	 *    - 处理服务端协议版本协商
	 *    - 获取服务端能力支持情况
	 *    - 初始化连接参数
	 * 
	 * 2. 全量快照(snapshot_full)：
	 *    - 接收服务端完整的玩家/实体/路标数据
	 *    - 替换本地缓存实现同步
	 *    - 用于初始同步或强制刷新
	 * 
	 * 3. 增量补丁(patch)：
	 *    - 接收服务端发送的差分更新
	 *    - 高效更新本地缓存
	 *    - 支持upsert(更新插入)和delete(删除)操作
	 * 
	 * 4. 摘要校验(digest)：
	 *    - 接收服务端数据摘要哈希
	 *    - 对比本地数据一致性
	 *    - 发现不一致时请求重同步
	 * 
	 * 5. 刷新请求(refresh_req)：
	 *    - 服务端要求客户端刷新特定数据
	 *    - 标记待刷新的玩家/实体ID
	 *    - 在下次更新时强制发送完整数据
	 * 
	 * 6. 路标相关消息：
	 *    - waypoints_update: 接收新的路标数据
	 *    - waypoints_delete: 处理路标删除通知
	 * 
	 * 7. 兼容性消息(positions)：
	 *    - 处理旧版本协议的遗留消息格式
	 *    - 确保向后兼容性
	 * 
	 * 通用处理：
	 * - 解析消息中的修订版本号(rev)
	 * - 更新本地版本跟踪
	 * - 统一的错误处理和日志记录
	 */
	private void processCompleteMessage(String message) {
		try {
			if (message == null || message.trim().isEmpty()) {
				LOGGER.warn("Received empty message");
				return;
			}

			JsonObject json;
			try {
				json = JsonParser.parseString(message).getAsJsonObject();
			} catch (JsonSyntaxException e) {
				LOGGER.error("Failed to parse JSON message: {}, message: {}", e.getMessage(), message);
				return;
			}

			String messageType = json.has("type") ? json.get("type").getAsString() : "";

			if (json.has("rev") && !json.get("rev").isJsonNull()) {
				try {
					lastServerRevision = Math.max(lastServerRevision, json.get("rev").getAsLong());
				} catch (Exception ignored) {
				}
			}

			if ("handshake_ack".equals(messageType)) {
				handleHandshakeAck(json);
				return;
			}

			if ("snapshot_full".equals(messageType)) {
				applySnapshot(json);
				return;
			}

			if ("patch".equals(messageType)) {
				applyPatch(json);
				return;
			}

			if ("digest".equals(messageType)) {
				handleDigest(json);
				return;
			}

			if ("refresh_req".equals(messageType)) {
				handleRefreshRequest(json);
				return;
			}

			if ("waypoints_update".equals(messageType)) {
				Map<String, SharedWaypointInfo> receivedWaypoints = parseWaypointsNode(json, "waypoints");
				if (!receivedWaypoints.isEmpty()) {
					remoteWaypointCache.putAll(receivedWaypoints);
					notifyWaypointsReceived(receivedWaypoints);
				}
				return;
			}

			if ("waypoints_delete".equals(messageType)) {
				List<String> waypointIds = parseWaypointDeleteIds(json);
				if (!waypointIds.isEmpty()) {
					for (String id : waypointIds) {
						remoteWaypointCache.remove(id);
					}
					notifyWaypointsDeleted(waypointIds);
				}
				return;
			}

			if ("positions".equals(messageType)) {
				applyLegacyPositions(json);
			}
		} catch (Exception e) {
			LOGGER.error("PlayerESP Network - Error processing complete message: {}, message: {}", e.getMessage(), message);
		}
	}

	private void applyLegacyPositions(JsonObject json) {
		if (json.has("players") && json.get("players").isJsonObject()) {
			Map<UUID, RemotePlayerInfo> latestRemotePlayers = parseRemotePlayers(json.getAsJsonObject("players"), true);
			reconcileRemotePlayers(latestRemotePlayers);
		}

		if (json.has("entities") && json.get("entities").isJsonObject()) {
			replaceEntityCache(json.getAsJsonObject("entities"));
		}

		if (json.has("waypoints") && json.get("waypoints").isJsonObject()) {
			remoteWaypointDataCache.clear();
			Map<String, SharedWaypointInfo> receivedWaypoints = parseWaypointsNode(json, "waypoints");
			remoteWaypointCache.clear();
			remoteWaypointCache.putAll(receivedWaypoints);
			if (!receivedWaypoints.isEmpty()) {
				notifyWaypointsReceived(receivedWaypoints);
			}
		}

		if (json.has("playerMarks") && json.get("playerMarks").isJsonObject()) {
			replacePlayerMarks(json.getAsJsonObject("playerMarks"));
		}
	}

	/**
	 * 应用全量快照数据 - 数据同步核心方法
	 * 
	 * 快照应用场景：
	 * 1. 初始连接后的首次数据同步
	 * 2. 强制重同步请求的响应
	 * 3. 协议版本升级后的数据重建
	 * 4. 检测到数据不一致时的恢复
	 * 
	 * 处理的数据类型：
	 * 
	 * 玩家数据(players):
	 * - 解析并替换所有远程玩家信息
	 * - 更新玩家位置缓存
	 * - 同步玩家维度和名称信息
	 * 
	 * 实体数据(entities):
	 * - 替换整个实体缓存
	 * - 包含怪物、物品、载具等世界实体
	 * - 更新实体位置和状态信息
	 * 
	 * 路标数据(waypoints):
	 * - 清空现有路标缓存
	 * - 解析新的路标数据
	 * - 通知监听器路标更新
	 * 
	 * 玩家标记(playerMarks):
	 * - 更新玩家队伍归属信息
	 * - 同步颜色标记和标签设置
	 * 
	 * 设计特点：
	 * - 完整替换而非增量更新
	 * - 确保数据的完整性和一致性
	 * - 适用于需要完全同步的场景
	 */
	private void applySnapshot(JsonObject json) {
		if (json.has("players") && json.get("players").isJsonObject()) {
			Map<UUID, RemotePlayerInfo> latestRemotePlayers = parseRemotePlayers(json.getAsJsonObject("players"), true);
			reconcileRemotePlayers(latestRemotePlayers);
		}

		if (json.has("entities") && json.get("entities").isJsonObject()) {
			replaceEntityCache(json.getAsJsonObject("entities"));
		}

		if (json.has("waypoints") && json.get("waypoints").isJsonObject()) {
			remoteWaypointDataCache.clear();
			Map<String, SharedWaypointInfo> receivedWaypoints = parseWaypointsNode(json, "waypoints");
			remoteWaypointCache.clear();
			remoteWaypointCache.putAll(receivedWaypoints);
			if (!receivedWaypoints.isEmpty()) {
				notifyWaypointsReceived(receivedWaypoints);
			}
		}

		if (json.has("playerMarks") && json.get("playerMarks").isJsonObject()) {
			replacePlayerMarks(json.getAsJsonObject("playerMarks"));
		}
	}

	/**
	 * 应用增量补丁数据 - 高效数据更新机制
	 * 
	 * 补丁协议优势：
	 * - 只传输变化的数据，大幅减少网络流量
	 * - 支持细粒度的增删改操作
	 * - 保持数据同步的同时提升性能
	 * 
	 * 支持的操作类型：
	 * 
	 * 删除操作(delete):
	 * - 从缓存中移除指定ID的记录
	 * - 清理相关的快照和位置数据
	 * - 释放内存资源
	 * 
	 * 更新插入操作(upsert):
	 * - 对于新记录：执行插入操作
	 * - 对于现有记录：执行更新操作
	 * - 智能合并字段变更
	 * 
	 * 处理的数据类别：
	 * 
	 * 玩家补丁(players):
	 * - 删除离线或退出的玩家
	 * - 更新在线玩家的位置和状态
	 * - 维护玩家缓存的一致性
	 * 
	 * 实体补丁(entities):
	 * - 处理实体生成、移动、销毁
	 * - 更新实体属性和位置信息
	 * - 保持世界状态同步
	 * 
	 * 路标补丁(waypoints):
	 * - 添加新的路标记录
	 * - 删除被移除的路标
	 * - 通知UI层更新显示
	 * 
	 * 玩家标记补丁(playerMarks):
	 * - 更新队伍归属关系
	 * - 修改颜色标记设置
	 * - 同步玩家分组信息
	 * 
	 * 协议格式示例：
	 * {
	 *   "players": {
	 *     "delete": ["玩家ID1", "玩家ID2"],
	 *     "upsert": {
	 *       "玩家ID3": {"x": 100, "y": 64, "z": 200}
	 *     }
	 *   }
	 * }
	 */
	private void applyPatch(JsonObject json) {
		if (json.has("players") && json.get("players").isJsonObject()) {
			JsonObject playersPatch = json.getAsJsonObject("players");

			if (playersPatch.has("delete") && playersPatch.get("delete").isJsonArray()) {
				for (JsonElement idElement : playersPatch.getAsJsonArray("delete")) {
					if (idElement != null && idElement.isJsonPrimitive()) {
						try {
							String playerIdRaw = idElement.getAsString();
							UUID playerId = UUID.fromString(playerIdRaw);
							remotePlayers.remove(playerId);
							playerPositions.remove(playerId);
							remotePlayerDataCache.remove(playerId);
							lastSentPlayersSnapshot.remove(playerIdRaw);
						} catch (Exception ignored) {
						}
					}
				}
			}

			if (playersPatch.has("upsert") && playersPatch.get("upsert").isJsonObject()) {
				applyPlayerPatchUpserts(playersPatch.getAsJsonObject("upsert"));
			}
		}

		if (json.has("entities") && json.get("entities").isJsonObject()) {
			JsonObject entitiesPatch = json.getAsJsonObject("entities");
			if (entitiesPatch.has("delete") && entitiesPatch.get("delete").isJsonArray()) {
				for (JsonElement idElement : entitiesPatch.getAsJsonArray("delete")) {
					if (idElement != null && idElement.isJsonPrimitive()) {
						String entityId = idElement.getAsString();
						if (entityId != null && !entityId.isBlank()) {
							remoteEntityDataCache.remove(entityId);
							lastSentEntitiesSnapshot.remove(entityId);
						}
					}
				}
			}

			if (entitiesPatch.has("upsert") && entitiesPatch.get("upsert").isJsonObject()) {
				mergeEntityPatchUpsert(entitiesPatch.getAsJsonObject("upsert"));
			}
		}

		if (json.has("waypoints") && json.get("waypoints").isJsonObject()) {
			JsonObject waypointPatch = json.getAsJsonObject("waypoints");

			if (waypointPatch.has("delete") && waypointPatch.get("delete").isJsonArray()) {
				List<String> deleteIds = new ArrayList<>();
				for (JsonElement idElement : waypointPatch.getAsJsonArray("delete")) {
					if (idElement != null && idElement.isJsonPrimitive()) {
						String id = idElement.getAsString();
						if (id != null && !id.isBlank()) {
							remoteWaypointCache.remove(id);
							remoteWaypointDataCache.remove(id);
							deleteIds.add(id);
						}
					}
				}
				if (!deleteIds.isEmpty()) {
					notifyWaypointsDeleted(deleteIds);
				}
			}

			if (waypointPatch.has("upsert") && waypointPatch.get("upsert").isJsonObject()) {
				Map<String, SharedWaypointInfo> upserts = parseWaypointsFromObject(waypointPatch.getAsJsonObject("upsert"));
				if (!upserts.isEmpty()) {
					remoteWaypointCache.putAll(upserts);
					notifyWaypointsReceived(upserts);
				}
			}
		}

		if (json.has("playerMarks") && json.get("playerMarks").isJsonObject()) {
			JsonObject playerMarksNode = json.getAsJsonObject("playerMarks");
			if (playerMarksNode.has("upsert") || playerMarksNode.has("delete")) {
				applyPlayerMarksPatch(playerMarksNode);
			} else {
				replacePlayerMarks(playerMarksNode);
			}
		}
	}

	private void replacePlayerMarks(JsonObject marksJson) {
		remotePlayerMarks.clear();
		mergePlayerMarkUpserts(marksJson);
	}

	private void applyPlayerMarksPatch(JsonObject patchNode) {
		if (patchNode.has("delete") && patchNode.get("delete").isJsonArray()) {
			for (JsonElement idElement : patchNode.getAsJsonArray("delete")) {
				if (idElement == null || !idElement.isJsonPrimitive()) {
					continue;
				}
				String normalized = normalizePlayerMarkId(idElement.getAsString());
				if (normalized != null) {
					remotePlayerMarks.remove(normalized);
				}
			}
		}

		if (patchNode.has("upsert") && patchNode.get("upsert").isJsonObject()) {
			mergePlayerMarkUpserts(patchNode.getAsJsonObject("upsert"));
		}
	}

	private void mergePlayerMarkUpserts(JsonObject upsertNode) {
		for (Map.Entry<String, JsonElement> entry : upsertNode.entrySet()) {
			try {
				if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
					continue;
				}
				String normalizedId = normalizePlayerMarkId(entry.getKey());
				if (normalizedId == null) {
					continue;
				}

				JsonObject markNode = extractDataNode(entry.getValue().getAsJsonObject());
				String team = normalizeMarkTeam(getOptionalString(markNode, "team"));
				Integer color = parseColorValue(markNode.get("color"));
				String label = getOptionalString(markNode, "label");
				if (label != null && label.isBlank()) {
					label = null;
				}

				remotePlayerMarks.put(normalizedId, new PlayerMarkState(team, color, label));
			} catch (Exception e) {
				LOGGER.warn("Failed to parse player mark {}: {}", entry.getKey(), e.getMessage());
			}
		}
	}

	private String normalizePlayerMarkId(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value.trim()).toString().toLowerCase();
		} catch (Exception e) {
			return null;
		}
	}

	private String normalizeMarkTeam(String value) {
		if (value == null) {
			return "neutral";
		}
		String text = value.trim().toLowerCase();
		if ("friendly".equals(text) || "friend".equals(text) || "ally".equals(text) || "blue".equals(text)) {
			return "friendly";
		}
		if ("enemy".equals(text) || "hostile".equals(text) || "red".equals(text)) {
			return "enemy";
		}
		return "neutral";
	}

	private Integer parseColorValue(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		try {
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
				return element.getAsInt();
			}
			if (element.isJsonPrimitive()) {
				String text = element.getAsString();
				if (text == null || text.isBlank()) {
					return null;
				}
				String normalized = text.trim();
				if (normalized.startsWith("#")) {
					String hex = normalized.substring(1);
					if (hex.length() == 6) {
						return (0xFF << 24) | Integer.parseInt(hex, 16);
					}
					if (hex.length() == 8) {
						return (int) Long.parseLong(hex, 16);
					}
				}
				if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
					return (int) Long.parseLong(normalized.substring(2), 16);
				}
				return (int) Long.parseLong(normalized, 16);
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	/**
	 * 处理数据摘要校验消息 - 数据一致性保障机制
	 * 
	 * 工作原理：
	 * 1. 服务端定期发送各类数据的SHA-1摘要哈希
	 * 2. 客户端计算本地对应数据的摘要哈希
	 * 3. 对比双方哈希值是否一致
	 * 4. 发现不一致时请求强制重同步
	 * 
	 * 校验的数据类型：
	 * - players: 玩家位置数据摘要
	 * - entities: 实体位置数据摘要
	 * - waypoints: 路标数据摘要
	 * 
	 * 防抖机制：
	 * - 3秒冷却时间防止频繁重同步请求
	 * - 避免网络拥塞和服务端压力
	 * 
	 * 重同步触发条件：
	 * - 任一类型数据哈希不匹配
	 * - 超过冷却时间限制
	 * - 通过resync_req消息请求全量数据
	 * 
	 * 优势：
	 * - 及早发现数据不同步问题
	 * - 自动恢复数据一致性
	 * - 减少手动干预需求
	 */
	private void handleDigest(JsonObject json) {
		if (!json.has("hashes") || !json.get("hashes").isJsonObject()) {
			return;
		}

		JsonObject hashes = json.getAsJsonObject("hashes");
		String serverPlayerHash = getOptionalString(hashes, "players");
		String serverEntityHash = getOptionalString(hashes, "entities");
		String serverWaypointHash = getOptionalString(hashes, "waypoints");

		String localPlayerHash = computePlayersDigest();
		String localEntityHash = computeEntitiesDigest();
		String localWaypointHash = computeWaypointDigest();

		boolean mismatch = !Objects.equals(serverPlayerHash, localPlayerHash)
				|| !Objects.equals(serverEntityHash, localEntityHash)
				|| !Objects.equals(serverWaypointHash, localWaypointHash);

		if (!mismatch) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastResyncRequestMs < RESYNC_COOLDOWN_MS) {
			return;
		}

		lastResyncRequestMs = now;
		sendResyncRequest("digest_mismatch");
	}

	private void sendResyncRequest(String reason) {
		if (webSocket == null || !isConnected) {
			return;
		}
		try {
			JsonObject req = new JsonObject();
			req.addProperty("type", "resync_req");
			req.addProperty("reason", reason);
			req.addProperty("ackRev", lastServerRevision);
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null) {
				req.addProperty("submitPlayerId", client.player.getUuid().toString());
			}
			webSocket.send(gson.toJson(req));
		} catch (Exception e) {
			LOGGER.warn("Failed to send resync request: {}", e.getMessage());
		}
	}

	/**
	 * WebSocket连接关闭回调 - 连接终止处理
	 * 
	 * 状态码含义：
	 * - 1000: 正常关闭(客户端主动断开)
	 * - 其他: 异常关闭(网络问题、服务端关闭等)
	 * 
	 * 处理流程：
	 * 1. 通过任务队列切换到主线程
	 * 2. 更新连接状态为断开
	 * 3. 记录关闭原因(异常关闭时)
	 * 4. 重置协议协商状态
	 * 5. 清空本地快照缓存
	 * 6. 通知所有监听器连接已断开
	 * 7. 记录断开日志
	 * 8. 如需重连则调度重连任务
	 * 
	 * 线程安全：与onOpen保持一致的处理模式
	 */
	@Override
	public void onClosed(WebSocket webSocket, int statusCode, String reason) {
		// 关闭事件也切回主线程，统一处理状态重置与重连调度。
		enqueueMainThreadTask(() -> {
			isConnected = false;
			if (statusCode != 1000) {
				lastConnectionError = "WebSocket closed (" + statusCode + "): "
						+ (reason == null || reason.isBlank() ? "unknown reason" : reason);
			} else {
				lastConnectionError = "";
			}
			resetNegotiationState();
			clearLocalOutboundSnapshots();
			notifyConnectionStatusChanged(false);
			LOGGER.info("Disconnected from PlayerESP server. Status: {}, Reason: {}", statusCode, reason);
			if (shouldReconnect) {
				scheduleReconnect();
			}
		});
	}

	/**
	 * WebSocket连接失败回调 - 网络异常处理
	 * 
	 * 错误类型包括：
	 * - 网络连接超时
	 * - DNS解析失败
	 * - SSL/TLS握手失败
	 * - 代理配置错误
	 * - 服务端拒绝连接
	 * 
	 * 处理策略：
	 * 1. 通过任务队列确保主线程处理
	 * 2. 记录详细的错误信息
	 * 3. 设置连接状态为断开
	 * 4. 格式化错误原因便于诊断
	 * 5. 重置所有网络状态
	 * 6. 通知监听器连接失败
	 * 7. 如需重连则启动重连机制
	 * 
	 * 错误信息格式化：
	 * - 提取异常链中的关键信息
	 * - 合并多个异常的原因描述
	 * - 提供清晰的错误诊断信息
	 */
	@Override
	public void onFailure(WebSocket webSocket, Throwable error, Response response) {
		// 失败事件在网络线程触发，这里只入队，保证状态清理和通知时序一致。
		enqueueMainThreadTask(() -> {
			LOGGER.error("PlayerESP network error: {}", error.getMessage());
			isConnected = false;
			lastConnectionError = formatThrowableReason(error);
			resetNegotiationState();
			clearLocalOutboundSnapshots();
			notifyConnectionStatusChanged(false);
			if (shouldReconnect) {
				scheduleReconnect();
			}
		});
	}

	public static String getServerURL() {
		return config != null ? config.getServerURL() : "ws://localhost:8080/playeresp";
	}

	public static void setServerURL(String serverURL) {
		if (config != null) {
			config.setServerURL(serverURL);
		}
	}

		public static String getRoomCode() {
			return config != null ? config.getRoomCode() : "default";
		}

		public static void setRoomCode(String roomCode) {
			if (config != null) {
				config.setRoomCode(roomCode);
			}
		}

	public static boolean isUseSystemProxy() {
		return config == null || config.isUseSystemProxy();
	}

	public static void setUseSystemProxy(boolean useSystemProxy) {
		if (config != null) {
			config.setUseSystemProxy(useSystemProxy);
		}
	}

	public boolean isConnected() {
		return isConnected;
	}

	public String getLastConnectionError() {
		return lastConnectionError;
	}

	public Vec3d getRemoteEntityPosition(String entityId, String expectedDimension) {
		if (entityId == null || entityId.isBlank()) {
			return null;
		}

		Map<String, Object> data = remoteEntityDataCache.get(entityId);
		if (data == null) {
			return null;
		}

		if (expectedDimension != null && !expectedDimension.isBlank()) {
			Object dimension = data.get("dimension");
			if (dimension == null || !expectedDimension.equals(String.valueOf(dimension))) {
				return null;
			}
		}

		Double x = getAsDouble(data.get("x"));
		Double y = getAsDouble(data.get("y"));
		Double z = getAsDouble(data.get("z"));
		if (x == null || y == null || z == null) {
			return null;
		}

		return new Vec3d(x, y, z);
	}

	public Vec3d getRemotePlayerPosition(String playerId, String playerName, String expectedDimension) {
		UUID expectedUuid = null;
		if (playerId != null && !playerId.isBlank()) {
			try {
				expectedUuid = UUID.fromString(playerId);
			} catch (IllegalArgumentException ignored) {
			}
		}

		if (expectedUuid != null) {
			RemotePlayerInfo info = remotePlayers.get(expectedUuid);
			if (isRemotePlayerMatch(info, playerName, expectedDimension)) {
				return info.position();
			}
		}

		for (RemotePlayerInfo info : remotePlayers.values()) {
			if (isRemotePlayerMatch(info, playerName, expectedDimension)) {
				return info.position();
			}
		}

		return null;
	}

	public String getPlayerMarkTeam(UUID playerId) {
		if (playerId == null) {
			return null;
		}
		PlayerMarkState mark = remotePlayerMarks.get(playerId.toString().toLowerCase());
		return mark == null ? null : mark.team();
	}

	private boolean isRemotePlayerMatch(RemotePlayerInfo info, String expectedPlayerName, String expectedDimension) {
		if (info == null || info.position() == null || info.dimension() == null) {
			return false;
		}

		if (expectedDimension != null && !expectedDimension.isBlank()) {
			String actualDimension = info.dimension().getValue().toString();
			if (!expectedDimension.equals(actualDimension)) {
				return false;
			}
		}

		if (expectedPlayerName == null || expectedPlayerName.isBlank()) {
			return true;
		}

		String actualName = info.name();
		return actualName != null && actualName.equalsIgnoreCase(expectedPlayerName);
	}

	private String formatThrowableReason(Throwable throwable) {
		if (throwable == null) {
			return "Unknown error";
		}

		StringBuilder details = new StringBuilder();
		Throwable current = throwable;
		int depth = 0;
		while (current != null && depth < 6) {
			String message = current.getMessage();
			String type = current.getClass().getSimpleName();
			if (message != null && !message.isBlank()) {
				if (details.length() > 0) {
					details.append(" | caused by: ");
				}
				details.append(type).append(": ").append(message.trim());
			}
			current = current.getCause();
			depth++;
		}

		if (details.length() > 0) {
			return details.toString();
		}

		String fallback = throwable.toString();
		if (fallback != null && !fallback.isBlank()) {
			return fallback;
		}
		return throwable.getClass().getSimpleName();
	}

	private RegistryKey<World> getCurrentDimension() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null) {
			return client.world.getRegistryKey();
		}
		return World.OVERWORLD;
	}

	/**
	 * 发送握手消息 - 协议协商初始化
	 * 
	 * 握手协议格式：
	 * {
	 *   "type": "handshake",
	 *   "networkProtocolVersion": "客户端协议版本",
	 *   "protocolVersion": "协议版本(冗余字段)",
	 *   "localProgramVersion": "客户端程序版本",
	 *   "roomCode": "房间代码",
	 *   "supportsDelta": true,
	 *   "submitPlayerId": "玩家UUID"
	 * }
	 * 
	 * 协商目的：
	 * 1. 向服务端声明客户端能力
	 * 2. 协商双方支持的协议版本
	 * 3. 确认是否支持增量更新功能
	 * 4. 建立房间归属关系
	 * 5. 交换客户端标识信息
	 * 
	 * 服务端响应：handshake_ack消息确认协商结果
	 */
	private void sendHandshake() {
		if (webSocket == null || !isConnected)
			return;

		try {
			JsonObject handshake = new JsonObject();
			handshake.addProperty("type", "handshake");
			handshake.addProperty("networkProtocolVersion", CLIENT_PROTOCOL_VERSION);
			handshake.addProperty("protocolVersion", CLIENT_PROTOCOL_VERSION);
			handshake.addProperty("localProgramVersion", CLIENT_PROGRAM_VERSION);
			handshake.addProperty("roomCode", getRoomCode());
			handshake.addProperty("supportsDelta", true);

			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null) {
				handshake.addProperty("submitPlayerId", client.player.getUuid().toString());
			}

			webSocket.send(gson.toJson(handshake));
			LOGGER.info("Sent handshake message");
		} catch (Exception e) {
			LOGGER.error("Failed to send handshake message: {}", e.getMessage());
		}
	}

	/**
	 * 处理握手确认消息 - 协议协商完成
	 * 
	 * 从服务端响应中提取的关键信息：
	 * 
	 * 1. 协议版本兼容性：
	 *    - serverProtocolVersion: 服务端协议版本
	 *    - 用于后续功能兼容性判断
	 * 
	 * 2. 增量更新支持：
	 *    - serverSupportsDelta: 是否支持差分同步
	 *    - 影响数据传输策略选择
	 * 
	 * 3. 数据一致性配置：
	 *    - digestIntervalSec: 摘要校验间隔时间
	 *    - 控制数据同步频率
	 * 
	 * 4. 初始状态同步：
	 *    - lastServerRevision: 服务端初始修订版本
	 *    - 作为后续增量更新的基准版本
	 * 
	 * 5. 程序版本信息：
	 *    - serverProgramVersion: 服务端程序版本
	 *    - 用于调试和版本对比
	 */
	private void handleHandshakeAck(JsonObject json) {
		if (json.has("ready") && json.get("ready").getAsBoolean()) {
			serverProtocolVersion = readProtocolVersionFromHandshakeAck(json);
			serverProgramVersion = readProgramVersionFromHandshakeAck(json);
			serverSupportsDelta = json.has("deltaEnabled") && json.get("deltaEnabled").getAsBoolean();
			digestIntervalSec = json.has("digestIntervalSec") ? json.get("digestIntervalSec").getAsInt() : 10;
			if (json.has("rev") && !json.get("rev").isJsonNull()) {
				lastServerRevision = json.get("rev").getAsLong();
			}
			LOGGER.info("Handshake completed: protocol={}, serverProgramVersion={}, delta={}, digestInterval={}s",
					serverProtocolVersion, serverProgramVersion, serverSupportsDelta, digestIntervalSec);
		}
	}

	private String readProtocolVersionFromHandshakeAck(JsonObject json) {
		try {
			if (json.has("networkProtocolVersion") && !json.get("networkProtocolVersion").isJsonNull()) {
				String value = json.get("networkProtocolVersion").getAsString();
				if (value != null && !value.isBlank()) {
					return value;
				}
			}
			if (json.has("protocolVersion") && !json.get("protocolVersion").isJsonNull()) {
				String value = json.get("protocolVersion").getAsString();
				if (value != null && !value.isBlank()) {
					return value;
				}
			}
		} catch (Exception ignored) {
		}
		return "0.0.0";
	}

	private String readProgramVersionFromHandshakeAck(JsonObject json) {
		if (json.has("localProgramVersion") && !json.get("localProgramVersion").isJsonNull()) {
			try {
				String value = json.get("localProgramVersion").getAsString();
				if (value != null && !value.isBlank()) {
					return value;
				}
			} catch (Exception ignored) {
			}
		}
		if (json.has("programVersion") && !json.get("programVersion").isJsonNull()) {
			try {
				String value = json.get("programVersion").getAsString();
				if (value != null && !value.isBlank()) {
					return value;
				}
			} catch (Exception ignored) {
			}
		}
		return "unknown";
	}

	private static String resolveLocalProgramVersion() {
		try {
			return FabricLoader.getInstance()
					.getModContainer("teamviewer")
					.map(container -> container.getMetadata().getVersion().getFriendlyString())
					.orElse("teamviewer-mod-dev");
		} catch (Exception ignored) {
			return "teamviewer-mod-dev";
		}
	}

	private void notifyConnectionStatusChanged(boolean connected) {
		for (ConnectionStatusListener listener : statusListeners) {
			try {
				listener.onConnectionStatusChanged(connected);
			} catch (Exception e) {
				LOGGER.error("Error notifying connection status listener: {}", e.getMessage());
			}
		}
	}

	private void notifyWaypointsReceived(Map<String, SharedWaypointInfo> waypoints) {
		for (WaypointUpdateListener listener : waypointListeners) {
			try {
				listener.onWaypointsReceived(waypoints);
			} catch (Exception e) {
				LOGGER.error("Error notifying waypoint listener: {}", e.getMessage());
			}
		}
	}

	private void notifyWaypointsDeleted(List<String> waypointIds) {
		for (WaypointUpdateListener listener : waypointListeners) {
			try {
				listener.onWaypointsDeleted(waypointIds);
			} catch (Exception e) {
				LOGGER.error("Error notifying waypoint delete listener: {}", e.getMessage());
			}
		}
	}

	private Map<UUID, RemotePlayerInfo> parseRemotePlayers(JsonObject playersJson, boolean replaceCache) {
		Map<UUID, RemotePlayerInfo> newRemotePlayers = new HashMap<>();
		RegistryKey<World> fallbackDimension = getCurrentDimension();

		if (replaceCache) {
			remotePlayerDataCache.clear();
		}

		for (Map.Entry<String, JsonElement> entry : playersJson.entrySet()) {
			try {
				String playerIdStr = entry.getKey();
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject playerDataNode = entry.getValue().getAsJsonObject();
				JsonObject actualData = extractDataNode(playerDataNode);
				UUID playerId = UUID.fromString(playerIdStr);
				Map<String, Object> mergedData = new HashMap<>();
				if (!replaceCache && remotePlayerDataCache.containsKey(playerId)) {
					mergedData.putAll(remotePlayerDataCache.get(playerId));
				}
				mergedData.putAll(jsonObjectToValueMap(actualData));

				RemotePlayerInfo info = buildRemotePlayerInfo(playerId, mergedData, fallbackDimension, playerIdStr);
				if (info == null) {
					continue;
				}

				remotePlayerDataCache.put(playerId, mergedData);
				newRemotePlayers.put(playerId, info);
			} catch (Exception e) {
				LOGGER.error("PlayerESP Network - Error parsing player data: {}", e.getMessage());
			}
		}

		return newRemotePlayers;
	}

	private void applyPlayerPatchUpserts(JsonObject upsertJson) {
		RegistryKey<World> fallbackDimension = getCurrentDimension();

		for (Map.Entry<String, JsonElement> entry : upsertJson.entrySet()) {
			try {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}

				UUID playerId = UUID.fromString(entry.getKey());
				Map<String, Object> mergedData = new HashMap<>();
				Map<String, Object> existing = remotePlayerDataCache.get(playerId);
				if (existing != null) {
					mergedData.putAll(existing);
				}

				JsonObject dataNode = extractDataNode(entry.getValue().getAsJsonObject());
				mergedData.putAll(jsonObjectToValueMap(dataNode));

				RemotePlayerInfo info = buildRemotePlayerInfo(playerId, mergedData, fallbackDimension, entry.getKey());
				if (info == null) {
					continue;
				}

				remotePlayerDataCache.put(playerId, mergedData);
				remotePlayers.put(playerId, info);
				playerPositions.put(playerId, info.position());
			} catch (Exception e) {
				LOGGER.error("PlayerESP Network - Error applying player patch: {}", e.getMessage());
			}
		}
	}

	private RemotePlayerInfo buildRemotePlayerInfo(UUID playerId, Map<String, Object> mergedData,
			RegistryKey<World> fallbackDimension, String fallbackName) {
		Double x = getAsDouble(mergedData.get("x"));
		Double y = getAsDouble(mergedData.get("y"));
		Double z = getAsDouble(mergedData.get("z"));
		if (x == null || y == null || z == null) {
			return null;
		}

		String dimensionId = mergedData.get("dimension") == null ? null : String.valueOf(mergedData.get("dimension"));
		RegistryKey<World> dimension = RemotePlayerInfo.parseDimension(dimensionId, fallbackDimension);
		String playerName = mergedData.get("playerName") == null ? fallbackName : String.valueOf(mergedData.get("playerName"));

		Vec3d position = new Vec3d(x, y, z);
		return new RemotePlayerInfo(playerId, position, dimension, playerName);
	}

	private Double getAsDouble(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		}
		try {
			return Double.parseDouble(String.valueOf(value));
		} catch (Exception e) {
			return null;
		}
	}

	private Map<String, Object> jsonObjectToValueMap(JsonObject jsonObject) {
		Map<String, Object> values = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
			values.put(entry.getKey(), jsonElementToValue(entry.getValue()));
		}
		return values;
	}

	private Object jsonElementToValue(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (element.isJsonPrimitive()) {
			if (element.getAsJsonPrimitive().isBoolean()) {
				return element.getAsBoolean();
			}
			if (element.getAsJsonPrimitive().isNumber()) {
				return element.getAsDouble();
			}
			return element.getAsString();
		}
		return element.toString();
	}

	private Map<String, Object> computeFieldDelta(Map<String, Object> previous, Map<String, Object> current) {
		Map<String, Object> delta = new HashMap<>();
		for (Map.Entry<String, Object> entry : current.entrySet()) {
			if (!Objects.equals(previous.get(entry.getKey()), entry.getValue())) {
				delta.put(entry.getKey(), entry.getValue());
			}
		}
		return delta;
	}

	private boolean shouldForcePlayersFullRefresh() {
		long now = System.currentTimeMillis();
		return now - lastPlayersPacketSentMs >= FORCE_FULL_REFRESH_MS;
	}

	private boolean shouldForceEntitiesFullRefresh() {
		long now = System.currentTimeMillis();
		return now - lastEntitiesPacketSentMs >= FORCE_FULL_REFRESH_MS;
	}

	private void handleRefreshRequest(JsonObject json) {
		List<String> players = parseStringArrayField(json, "players");
		List<String> entities = parseStringArrayField(json, "entities");

		pendingPlayerRefreshIds.addAll(players);
		pendingEntityRefreshIds.addAll(entities);

		if (!players.isEmpty() || !entities.isEmpty()) {
			LOGGER.info("Received refresh_req: players={}, entities={}", players.size(), entities.size());
		}
	}

	private List<String> parseStringArrayField(JsonObject json, String fieldName) {
		if (!json.has(fieldName) || !json.get(fieldName).isJsonArray()) {
			return List.of();
		}

		List<String> result = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray(fieldName)) {
			if (element == null || !element.isJsonPrimitive()) {
				continue;
			}
			String value = element.getAsString();
			if (value != null && !value.isBlank()) {
				result.add(value);
			}
		}
		return result;
	}

	private void applyPendingPlayerRefresh(
			Map<String, Map<String, Object>> currentSnapshot,
			Map<String, Map<String, Object>> upsert,
			List<String> delete
	) {
		if (pendingPlayerRefreshIds.isEmpty()) {
			return;
		}

		Set<String> deleteSet = new HashSet<>(delete);
		for (String playerId : new ArrayList<>(pendingPlayerRefreshIds)) {
			Map<String, Object> fullData = currentSnapshot.get(playerId);
			if (fullData != null) {
				upsert.put(playerId, fullData);
			} else {
				deleteSet.add(playerId);
			}
			pendingPlayerRefreshIds.remove(playerId);
		}

		delete.clear();
		delete.addAll(deleteSet);
	}

	private void applyPendingEntityRefresh(
			Map<String, Map<String, Object>> currentSnapshot,
			Map<String, Map<String, Object>> upsert,
			List<String> delete
	) {
		if (pendingEntityRefreshIds.isEmpty()) {
			return;
		}

		Set<String> deleteSet = new HashSet<>(delete);
		for (String entityId : new ArrayList<>(pendingEntityRefreshIds)) {
			Map<String, Object> fullData = currentSnapshot.get(entityId);
			if (fullData != null) {
				upsert.put(entityId, fullData);
			} else {
				deleteSet.add(entityId);
			}
			pendingEntityRefreshIds.remove(entityId);
		}

		delete.clear();
		delete.addAll(deleteSet);
	}

	private JsonObject extractDataNode(JsonObject node) {
		if (node.has("data") && node.get("data").isJsonObject()) {
			return node.getAsJsonObject("data");
		}
		return node;
	}

	private Map<String, SharedWaypointInfo> parseWaypointsNode(JsonObject json, String fieldName) {
		if (!json.has(fieldName) || !json.get(fieldName).isJsonObject()) {
			return Map.of();
		}
		return parseWaypointsFromObject(json.getAsJsonObject(fieldName));
	}

	private Map<String, SharedWaypointInfo> parseWaypointsFromObject(JsonObject waypointsJson) {
		Map<String, SharedWaypointInfo> result = new HashMap<>();

		for (Map.Entry<String, JsonElement> entry : waypointsJson.entrySet()) {
			try {
				String waypointId = entry.getKey();
				if (!entry.getValue().isJsonObject()) {
					continue;
				}

				JsonObject node = entry.getValue().getAsJsonObject();
				JsonObject data = extractDataNode(node);
				Map<String, Object> rawData = jsonObjectToValueMap(data);
				remoteWaypointDataCache.put(waypointId, rawData);

				if (!data.has("x") || !data.has("y") || !data.has("z")) {
					continue;
				}

				UUID ownerId = null;
				if (data.has("ownerId") && !data.get("ownerId").isJsonNull()) {
					ownerId = UUID.fromString(data.get("ownerId").getAsString());
				}

				String name = data.has("name") ? data.get("name").getAsString() : "Waypoint";
				String symbol = data.has("symbol") ? data.get("symbol").getAsString() : "W";
				String ownerName = data.has("ownerName") ? data.get("ownerName").getAsString() : "Unknown";
				String dimension = data.has("dimension") ? data.get("dimension").getAsString() : null;
				int color = data.has("color") ? data.get("color").getAsInt() : 0x55FF55;
				long createdAt = data.has("createdAt") ? data.get("createdAt").getAsLong() : System.currentTimeMillis();
				String targetType = data.has("targetType") && !data.get("targetType").isJsonNull()
						? data.get("targetType").getAsString()
						: null;
				String targetEntityId = data.has("targetEntityId") && !data.get("targetEntityId").isJsonNull()
						? data.get("targetEntityId").getAsString()
						: null;
				String targetEntityType = data.has("targetEntityType") && !data.get("targetEntityType").isJsonNull()
						? data.get("targetEntityType").getAsString()
						: null;
				String targetEntityName = data.has("targetEntityName") && !data.get("targetEntityName").isJsonNull()
						? data.get("targetEntityName").getAsString()
						: null;
				String waypointKind = data.has("waypointKind") && !data.get("waypointKind").isJsonNull()
						? data.get("waypointKind").getAsString()
						: null;

				SharedWaypointInfo waypoint = new SharedWaypointInfo(
						waypointId,
						ownerId,
						ownerName,
						name,
						symbol,
						data.get("x").getAsInt(),
						data.get("y").getAsInt(),
						data.get("z").getAsInt(),
						dimension,
						color,
						createdAt,
						targetType,
						targetEntityId,
						targetEntityType,
						targetEntityName,
						waypointKind);
				result.put(waypointId, waypoint);
			} catch (Exception e) {
				LOGGER.error("Failed to parse shared waypoint {}: {}", entry.getKey(), e.getMessage());
			}
		}

		return result;
	}

	private List<String> parseWaypointDeleteIds(JsonObject json) {
		if (!json.has("waypointIds") || !json.get("waypointIds").isJsonArray()) {
			return List.of();
		}

		List<String> result = new ArrayList<>();
		for (JsonElement idElement : json.getAsJsonArray("waypointIds")) {
			if (idElement != null && idElement.isJsonPrimitive()) {
				String id = idElement.getAsString();
				if (id != null && !id.isBlank()) {
					result.add(id);
				}
			}
		}

		return result;
	}

	private void reconcilePlayerPositions(Map<UUID, Vec3d> latestPositions) {
		playerPositions.entrySet().removeIf(entry -> !latestPositions.containsKey(entry.getKey()));
		for (Map.Entry<UUID, Vec3d> entry : latestPositions.entrySet()) {
			UUID playerId = entry.getKey();
			Vec3d latest = entry.getValue();
			Vec3d existing = playerPositions.get(playerId);
			if (!Objects.equals(existing, latest)) {
				playerPositions.put(playerId, latest);
			}
		}
	}

	private void reconcileRemotePlayers(Map<UUID, RemotePlayerInfo> latestRemotePlayers) {
		remotePlayers.entrySet().removeIf(entry -> !latestRemotePlayers.containsKey(entry.getKey()));
		for (Map.Entry<UUID, RemotePlayerInfo> entry : latestRemotePlayers.entrySet()) {
			UUID playerId = entry.getKey();
			RemotePlayerInfo latest = entry.getValue();
			RemotePlayerInfo existing = remotePlayers.get(playerId);
			if (!Objects.equals(existing, latest)) {
				remotePlayers.put(playerId, latest);
			}
		}

		Map<UUID, Vec3d> latestPositions = new HashMap<>();
		for (Map.Entry<UUID, RemotePlayerInfo> entry : remotePlayers.entrySet()) {
			latestPositions.put(entry.getKey(), entry.getValue().position());
		}
		reconcilePlayerPositions(latestPositions);
	}

	private String computePlayersDigest() {
		Map<String, Map<String, Object>> state = new HashMap<>();
		for (Map.Entry<UUID, Map<String, Object>> entry : remotePlayerDataCache.entrySet()) {
			state.put(entry.getKey().toString(), entry.getValue());
		}
		return stateDigest(state);
	}

	private String computeEntitiesDigest() {
		return stateDigest(remoteEntityDataCache);
	}

	private String computeWaypointDigest() {
		return stateDigest(remoteWaypointDataCache);
	}

	private void replaceEntityCache(JsonObject entitiesJson) {
		remoteEntityDataCache.clear();
		mergeEntityPatchUpsert(entitiesJson);
	}

	private void mergeEntityPatchUpsert(JsonObject upsertJson) {
		for (Map.Entry<String, JsonElement> entry : upsertJson.entrySet()) {
			try {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				String entityId = entry.getKey();
				JsonObject dataNode = extractDataNode(entry.getValue().getAsJsonObject());
				Map<String, Object> merged = new HashMap<>();
				Map<String, Object> existing = remoteEntityDataCache.get(entityId);
				if (existing != null) {
					merged.putAll(existing);
				}
				merged.putAll(jsonObjectToValueMap(dataNode));
				remoteEntityDataCache.put(entityId, merged);
			} catch (Exception e) {
				LOGGER.error("PlayerESP Network - Error applying entity patch: {}", e.getMessage());
			}
		}
	}

	private String stateDigest(Map<String, Map<String, Object>> state) {
		try {
			List<String> ids = new ArrayList<>(state.keySet());
			Collections.sort(ids);

			List<String> lines = new ArrayList<>();
			for (String id : ids) {
				Map<String, Object> data = state.get(id);
				lines.add(gson.toJson(id) + ":" + canonicalValue(data == null ? Map.of() : data));
			}

			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			for (String line : lines) {
				digest.update(line.getBytes(StandardCharsets.UTF_8));
				digest.update((byte) '\n');
			}
			byte[] bytes = digest.digest();
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 8 && i < bytes.length; i++) {
				hex.append(String.format("%02x", bytes[i]));
			}
			return hex.toString();
		} catch (Exception e) {
			return "hash_error";
		}
	}

	private String canonicalValue(Object value) {
		if (value == null) {
			return "null";
		}
		if (value instanceof Boolean boolValue) {
			return boolValue ? "true" : "false";
		}
		if (value instanceof Number numberValue) {
			return canonicalNumber(numberValue);
		}
		if (value instanceof String stringValue) {
			return gson.toJson(stringValue);
		}
		if (value instanceof Map<?, ?> mapValue) {
			List<String> keys = new ArrayList<>();
			for (Object key : mapValue.keySet()) {
				keys.add(String.valueOf(key));
			}
			Collections.sort(keys);

			StringBuilder builder = new StringBuilder("{");
			for (int i = 0; i < keys.size(); i++) {
				String key = keys.get(i);
				if (i > 0) {
					builder.append(',');
				}
				builder.append(gson.toJson(key)).append(':').append(canonicalValue(mapValue.get(key)));
			}
			builder.append('}');
			return builder.toString();
		}
		if (value instanceof List<?> listValue) {
			StringBuilder builder = new StringBuilder("[");
			for (int i = 0; i < listValue.size(); i++) {
				if (i > 0) {
					builder.append(',');
				}
				builder.append(canonicalValue(listValue.get(i)));
			}
			builder.append(']');
			return builder.toString();
		}

		return gson.toJson(value);
	}

	private String canonicalNumber(Number numberValue) {
		if (numberValue instanceof Byte || numberValue instanceof Short
				|| numberValue instanceof Integer || numberValue instanceof Long) {
			return String.valueOf(numberValue.longValue());
		}

		double value = numberValue.doubleValue();
		if (!Double.isFinite(value)) {
			return "null";
		}

		BigDecimal decimal = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
		String text = decimal.toPlainString();
		if ("-0".equals(text) || "".equals(text)) {
			return "0";
		}
		return text;
	}

	private String getOptionalString(JsonObject json, String key) {
		if (!json.has(key) || json.get(key).isJsonNull()) {
			return "";
		}
		try {
			return json.get(key).getAsString();
		} catch (Exception e) {
			return "";
		}
	}

	private Map<String, Object> copyValueMap(Map<String, Object> source) {
		Map<String, Object> copy = new HashMap<>();
		if (source == null) {
			return copy;
		}
		for (Map.Entry<String, Object> entry : source.entrySet()) {
			copy.put(entry.getKey(), entry.getValue());
		}
		return copy;
	}

	private void resetNegotiationState() {
		serverSupportsDelta = false;
		serverProtocolVersion = "0.0.0";
		serverProgramVersion = "unknown";
		digestIntervalSec = 10;
		lastServerRevision = 0;
		lastResyncRequestMs = 0L;
		lastPlayersPacketSentMs = 0L;
		lastEntitiesPacketSentMs = 0L;
		lastTabPlayersPacketSentMs = 0L;
	}

	private void clearLocalOutboundSnapshots() {
		lastSentPlayersSnapshot.clear();
		lastSentEntitiesSnapshot.clear();
		lastTabPlayersSignature = "";
		pendingPlayerRefreshIds.clear();
		pendingEntityRefreshIds.clear();
		remotePlayerDataCache.clear();
		remoteEntityDataCache.clear();
		remoteWaypointDataCache.clear();
		remoteWaypointCache.clear();
		remotePlayerMarks.clear();
	}
}
