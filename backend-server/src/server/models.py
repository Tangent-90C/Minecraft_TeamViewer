from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class PlayerData(BaseModel):
    """玩家状态上报模型（用于玩家位置与战斗属性同步）。"""
    x: float = Field(..., description="X坐标")
    y: float = Field(..., description="Y坐标")
    z: float = Field(..., description="Z坐标")
    vx: float = Field(default=0, description="X方向速度")
    vy: float = Field(default=0, description="Y方向速度")
    vz: float = Field(default=0, description="Z方向速度")
    dimension: str = Field(..., description="维度ID")
    playerName: Optional[str] = Field(None, description="玩家名称")
    playerUUID: Optional[str] = Field(None, description="玩家UUID")
    health: float = Field(default=0, ge=0, description="当前生命值")
    maxHealth: float = Field(default=20, ge=0, description="最大生命值")
    armor: float = Field(default=0, ge=0, description="护甲值")
    width: float = Field(default=0.6, gt=0, description="碰撞箱宽度")
    height: float = Field(default=1.8, gt=0, description="碰撞箱高度")

    model_config = ConfigDict(extra="ignore")


class EntityData(BaseModel):
    """实体状态上报模型（用于非玩家目标的可视化同步）。"""
    x: float = Field(..., description="X坐标")
    y: float = Field(..., description="Y坐标")
    z: float = Field(..., description="Z坐标")
    vx: float = Field(default=0, description="X方向速度")
    vy: float = Field(default=0, description="Y方向速度")
    vz: float = Field(default=0, description="Z方向速度")
    dimension: str = Field(..., description="维度ID")
    entityType: Optional[str] = Field(None, description="实体类型")
    entityName: Optional[str] = Field(None, description="实体名称")
    width: float = Field(default=0.6, ge=0, description="碰撞箱宽度")
    height: float = Field(default=1.8, ge=0, description="碰撞箱高度")

    model_config = ConfigDict(extra="ignore")


class WaypointData(BaseModel):
    """路标上报模型（支持普通路标与 quick 战术报点）。"""
    x: float = Field(..., description="X坐标")
    y: float = Field(..., description="Y坐标")
    z: float = Field(..., description="Z坐标")
    dimension: str = Field(..., description="维度ID")
    name: str = Field(..., description="路标名称")
    symbol: Optional[str] = Field("W", description="路标符号")
    color: int = Field(default=5635925, description="路标颜色")
    ownerId: Optional[str] = Field(None, description="创建者UUID")
    ownerName: Optional[str] = Field(None, description="创建者名称")
    createdAt: Optional[int] = Field(None, description="创建时间戳(ms)")
    ttlSeconds: Optional[int] = Field(None, ge=5, le=86400, description="路标超时秒数")
    waypointKind: Optional[str] = Field(None, description="路标类型: quick/manual")
    replaceOldQuick: Optional[bool] = Field(None, description="是否替换同玩家旧快捷报点")
    maxQuickMarks: Optional[int] = Field(None, ge=1, le=100, description="快捷报点最多保留数量")
    targetType: Optional[str] = Field(None, description="命中目标类型:block/entity")
    targetEntityId: Optional[str] = Field(None, description="命中实体UUID")
    targetEntityType: Optional[str] = Field(None, description="命中实体类型")
    targetEntityName: Optional[str] = Field(None, description="命中实体名称")

    model_config = ConfigDict(extra="ignore")
