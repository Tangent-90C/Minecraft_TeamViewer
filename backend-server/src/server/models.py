from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class PlayerData(BaseModel):
    """玩家状态上报模型（用于玩家位置与战斗属性同步）。"""
    x: float = Field(default=..., description="X坐标", json_schema_extra={"reliableTransport": False})
    y: float = Field(default=..., description="Y坐标", json_schema_extra={"reliableTransport": False})
    z: float = Field(default=..., description="Z坐标", json_schema_extra={"reliableTransport": False})
    vx: float = Field(default=0, description="X方向速度", json_schema_extra={"reliableTransport": False})
    vy: float = Field(default=0, description="Y方向速度", json_schema_extra={"reliableTransport": False})
    vz: float = Field(default=0, description="Z方向速度", json_schema_extra={"reliableTransport": False})
    dimension: str = Field(default=..., description="维度ID", json_schema_extra={"reliableTransport": True})
    playerName: Optional[str] = Field(default=None, description="玩家名称", json_schema_extra={"reliableTransport": True})
    playerUUID: Optional[str] = Field(default=None, description="玩家UUID", json_schema_extra={"reliableTransport": True})
    health: float = Field(default=0, ge=0, description="当前生命值", json_schema_extra={"reliableTransport": True})
    maxHealth: float = Field(default=20, ge=0, description="最大生命值", json_schema_extra={"reliableTransport": True})
    armor: float = Field(default=0, ge=0, description="护甲值", json_schema_extra={"reliableTransport": True})
    isRiding: bool = Field(default=False, description="是否正在骑马", json_schema_extra={"reliableTransport": True})
    width: float = Field(default=0.6, gt=0, description="碰撞箱宽度", json_schema_extra={"reliableTransport": True})
    height: float = Field(default=1.8, gt=0, description="碰撞箱高度", json_schema_extra={"reliableTransport": True})

    model_config = ConfigDict(extra="ignore")


class EntityData(BaseModel):
    """实体状态上报模型（用于非玩家目标的可视化同步）。"""
    x: float = Field(default=..., description="X坐标", json_schema_extra={"reliableTransport": False})
    y: float = Field(default=..., description="Y坐标", json_schema_extra={"reliableTransport": False})
    z: float = Field(default=..., description="Z坐标", json_schema_extra={"reliableTransport": False})
    vx: float = Field(default=0, description="X方向速度", json_schema_extra={"reliableTransport": False})
    vy: float = Field(default=0, description="Y方向速度", json_schema_extra={"reliableTransport": False})
    vz: float = Field(default=0, description="Z方向速度", json_schema_extra={"reliableTransport": False})
    dimension: str = Field(default=..., description="维度ID", json_schema_extra={"reliableTransport": False})
    entityType: Optional[str] = Field(default=None, description="实体类型", json_schema_extra={"reliableTransport": True})
    entityName: Optional[str] = Field(default=None, description="实体名称", json_schema_extra={"reliableTransport": True})
    width: float = Field(default=0.6, ge=0, description="碰撞箱宽度", json_schema_extra={"reliableTransport": True})
    height: float = Field(default=1.8, ge=0, description="碰撞箱高度", json_schema_extra={"reliableTransport": True})

    model_config = ConfigDict(extra="ignore")


class WaypointData(BaseModel):
    """路标上报模型（支持普通路标与 quick 战术报点）。"""
    x: float = Field(default=..., description="X坐标", json_schema_extra={"reliableTransport": False})
    y: float = Field(default=..., description="Y坐标", json_schema_extra={"reliableTransport": False})
    z: float = Field(default=..., description="Z坐标", json_schema_extra={"reliableTransport": False})
    dimension: str = Field(default=..., description="维度ID", json_schema_extra={"reliableTransport": True})
    name: str = Field(default=..., description="路标名称", json_schema_extra={"reliableTransport": True})
    symbol: Optional[str] = Field(default="W", description="路标符号", json_schema_extra={"reliableTransport": True})
    color: int = Field(default=5635925, description="路标颜色", json_schema_extra={"reliableTransport": True})
    ownerId: Optional[str] = Field(default=None, description="创建者UUID", json_schema_extra={"reliableTransport": True})
    ownerName: Optional[str] = Field(default=None, description="创建者名称", json_schema_extra={"reliableTransport": True})
    createdAt: Optional[int] = Field(default=None, description="创建时间戳(ms)", json_schema_extra={"reliableTransport": True})
    ttlSeconds: Optional[int] = Field(default=None, ge=5, le=86400, description="路标超时秒数", json_schema_extra={"reliableTransport": True})
    waypointKind: Optional[str] = Field(default=None, description="路标类型: quick/manual", json_schema_extra={"reliableTransport": True})
    replaceOldQuick: Optional[bool] = Field(default=None, description="是否替换同玩家旧快捷报点", json_schema_extra={"reliableTransport": True})
    maxQuickMarks: Optional[int] = Field(default=None, ge=1, le=100, description="快捷报点最多保留数量", json_schema_extra={"reliableTransport": True})
    targetType: Optional[str] = Field(default=None, description="命中目标类型:block/entity", json_schema_extra={"reliableTransport": True})
    targetEntityId: Optional[str] = Field(default=None, description="命中实体UUID", json_schema_extra={"reliableTransport": True})
    targetEntityType: Optional[str] = Field(default=None, description="命中实体类型", json_schema_extra={"reliableTransport": True})
    targetEntityName: Optional[str] = Field(default=None, description="命中实体名称", json_schema_extra={"reliableTransport": True})
    roomCode: Optional[str] = Field(default=None, description="房间号，用于房间隔离显示", json_schema_extra={"reliableTransport": True})
    permanent: Optional[bool] = Field(default=None, description="是否长期有效（不按默认TTL清理）", json_schema_extra={"reliableTransport": True})
    tacticalType: Optional[str] = Field(default=None, description="战术类型，如 attack/defend/gather", json_schema_extra={"reliableTransport": True})
    sourceType: Optional[str] = Field(default=None, description="来源类型，如 admin_tactical", json_schema_extra={"reliableTransport": True})

    model_config = ConfigDict(extra="ignore")
