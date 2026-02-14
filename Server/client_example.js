/**
 * JavaScript客户端示例 - 如何处理压缩的WebSocket数据
 * 
 * 使用pako库 (https://unpkg.com/pako@2/dist/pako.iife.js)
 * 
 * HTML中引入：
 *   <script src="https://unpkg.com/pako@2/dist/pako.iife.js"></script>
 *   <script src="websocket-client.js"></script>
 */

class CompressedWebSocketClient {
    constructor(serverUrl, playerId, enableCompression = true) {
        this.serverUrl = serverUrl;
        this.playerId = playerId;
        this.enableCompression = enableCompression;
        this.ws = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 10;
        this.reconnectDelay = 3000;
        this.onPositionsUpdate = null;  // 回调函数
        this.isHandshakeComplete = false;
    }

    /**
     * 解压gzip数据
     * @param {Uint8Array} compressedBuffer 压缩的字节数据
     * @returns {string} 解压后的JSON字符串
     */
    decompressData(compressedBuffer) {
        try {
            // 使用pako库解压gzip数据
            const decompressed = pako.ungzip(compressedBuffer);
            // 转换为字符串
            return new TextDecoder('utf-8').decode(decompressed);
        } catch (e) {
            console.error('Error decompressing data:', e);
            throw e;
        }
    }

    /**
     * 处理接收到的数据
     * @param {ArrayBuffer|string} data WebSocket接收到的数据
     */
    processMessage(data) {
        try {
            let jsonData;

            if (data instanceof ArrayBuffer) {
                // 二进制数据 - 检查压缩标志位
                const view = new Uint8Array(data);
                const compressionFlag = view[0];

                if (compressionFlag === 0x01) {
                    // 压缩数据
                    console.log('Receiving compressed data');
                    const compressedBuffer = view.slice(1);
                    const decompressed = this.decompressData(compressedBuffer);
                    jsonData = JSON.parse(decompressed);
                } else if (compressionFlag === 0x00) {
                    // 未压缩数据
                    console.log('Receiving uncompressed data');
                    const payload = view.slice(1);
                    const str = new TextDecoder('utf-8').decode(payload);
                    jsonData = JSON.parse(str);
                } else {
                    throw new Error(`Unknown compression flag: ${compressionFlag}`);
                }
            } else {
                // 字符串数据 - 直接作为JSON处理
                jsonData = JSON.parse(data);
            }

            // 处理消息
            if (jsonData.type === 'handshake_ack') {
                // 握手确认
                console.log(`Handshake confirmed, compression: ${jsonData.compressionEnabled ? 'enabled' : 'disabled'}`);
                this.isHandshakeComplete = true;
            } else if (jsonData.type === 'positions') {
                const players = jsonData.players || {};
                const entities = jsonData.entities || {};
                console.log(`Received - Players: ${Object.keys(players).length}, Entities: ${Object.keys(entities).length}`);

                // 触发回调
                if (this.onPositionsUpdate) {
                    this.onPositionsUpdate(players, entities);
                }
            }

        } catch (e) {
            console.error('Error processing message:', e);
        }
    }

    /**
     * 发送玩家数据更新
     * @param {Object} players 玩家数据字典 {playerId: {x, y, z, ...}}
     */
    async sendPlayersUpdate(players) {
        const message = {
            submitPlayerId: this.playerId,
            type: 'players_update',
            players: players
        };

        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            try {
                this.ws.send(JSON.stringify(message));
            } catch (e) {
                console.error('Error sending players update:', e);
            }
        }
    }

    /**
     * 发送实体数据更新
     * @param {Object} entities 实体数据字典 {entityId: {x, y, z, ...}}
     */
    async sendEntitiesUpdate(entities) {
        const message = {
            submitPlayerId: this.playerId,
            type: 'entities_update',
            entities: entities
        };

        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            try {
                this.ws.send(JSON.stringify(message));
            } catch (e) {
                console.error('Error sending entities update:', e);
            }
        }
    }

    /**
     * 连接到WebSocket服务器
     */
    connect() {
        try {
            this.ws = new WebSocket(this.serverUrl);
            this.ws.binaryType = 'arraybuffer';

            this.ws.onopen = () => {
                console.log('WebSocket connected to', this.serverUrl);
                this.reconnectAttempts = 0;
                
                // 发送握手消息，告知服务端是否使用压缩
                const handshakeMsg = {
                    type: 'handshake',
                    submitPlayerId: this.playerId,
                    enableCompression: this.enableCompression
                };
                this.ws.send(JSON.stringify(handshakeMsg));
            };

            this.ws.onmessage = (event) => {
                this.processMessage(event.data);
            };

            this.ws.onerror = (error) => {
                console.error('WebSocket error:', error);
            };

            this.ws.onclose = () => {
                console.log('WebSocket disconnected');
                this.isHandshakeComplete = false;
                this.attemptReconnect();
            };

        } catch (e) {
            console.error('Error connecting to WebSocket:', e);
            this.attemptReconnect();
        }
    }

    /**
     * 尝试重新连接
     */
    attemptReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            const delay = this.reconnectDelay * this.reconnectAttempts;
            console.log(`Attempting to reconnect in ${delay}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
            setTimeout(() => this.connect(), delay);
        } else {
            console.error('Max reconnection attempts reached');
        }
    }

    /**
     * 断开连接
     */
    disconnect() {
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
    }

    /**
     * 获取连接状态
     */
    isConnected() {
        return this.ws && this.ws.readyState === WebSocket.OPEN;
    }
}

// ============ 使用示例 ============

// 创建客户端实例（第3个参数设置是否启用压缩）
const client = new CompressedWebSocketClient(
    'ws://localhost:8765/playeresp',
    'player-' + Date.now(),
    true  // 启用压缩，改为 false 以禁用
);

// 设置数据更新回调
client.onPositionsUpdate = (players, entities) => {
    console.log('Players:', players);
    console.log('Entities:', entities);
    // 在这里处理数据：更新UI、游戏画面等
};

// 连接
client.connect();

// 示例：定期发送数据
setInterval(() => {
    if (client.isConnected()) {
        const testPlayers = {
            'player-001': {
                x: 100.5 + Math.random() * 10,
                y: 64.0,
                z: 200.5 + Math.random() * 10,
                vx: Math.random() * 0.2 - 0.1,
                vy: 0,
                vz: Math.random() * 0.2 - 0.1,
                dimension: 'minecraft:overworld',
                playerName: 'TestPlayer',
                playerUUID: '12345678-1234-1234-1234-123456789012',
                health: 20,
                maxHealth: 20,
                armor: 0
            }
        };

        client.sendPlayersUpdate(testPlayers);
    }
}, 500);

// 断连时的清理
window.addEventListener('beforeunload', () => {
    client.disconnect();
});
