# Minecraft_TeamViewer
本项目可以共享团队视野，目前仅在 1.21.8 版本上进行测试

web-server 文件夹下的 python 代码是用于配置服务端的，客户端把自己所看到的内容发送给服务端，服务端再把其他人看到的内容发送给客户端和管理端。

web-server/src/static/nodemc_map_projection.js 是一个油猴脚本，用于在MC服务器的地图上添加玩家视野信息。

剩下的就是这个 Minecraft 1.21.8 Fabric Mod 的代码本体了。