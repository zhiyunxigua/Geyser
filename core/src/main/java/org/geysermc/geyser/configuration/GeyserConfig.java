/*
 * Copyright (c) 2024 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.configuration;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.network.BedrockListener;
import org.geysermc.geyser.api.network.RemoteServer;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.text.AsteriskSerializer;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.CooldownUtils;
import org.spongepowered.configurate.interfaces.meta.Exclude;
import org.spongepowered.configurate.interfaces.meta.defaults.DefaultBoolean;
import org.spongepowered.configurate.interfaces.meta.defaults.DefaultNumeric;
import org.spongepowered.configurate.interfaces.meta.defaults.DefaultString;
import org.spongepowered.configurate.interfaces.meta.range.NumericRange;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@ConfigSerializable
public interface GeyserConfig {
    @Comment("Bedrock 监听器的网络设置")
    BedrockConfig bedrock();

    @Comment("Java 服务器连接的网络设置")
    JavaConfig java();

    @Comment("MOTD 设置")
    MotdConfig motd();

    @Comment("影响 Bedrock 玩家的游戏选项")
    GameplayConfig gameplay();

    @Comment("如果我们没有客户端请求的语言环境，则使用的默认语言环境。如果设置为 \"system\"，将使用系统的语言。")
    @DefaultString(GeyserLocale.SYSTEM_LOCALE)
    @NonNull
    String defaultLocale();

    @Comment("服务器是否记录玩家 IP 地址。")
    @DefaultBoolean(true)
    boolean logPlayerIpAddresses();

    @Comment("""
            仅适用于正版验证模式。
            存储一个 Bedrock 玩家用户名列表，这些玩家在登录后应保存其 Java版 账户信息。
            这会保存一个令牌，稍后可重复用于验证玩家身份。这不会保存邮箱或密码，
            但您仍应谨慎添加到此列表并授予他人访问此 Geyser 实例文件的权限。
            从此列表中删除一个名称将在下次 Geyser 启动时删除其缓存的登录信息。
            存储令牌的文件与此配置文件位于同一文件夹中，名为 "saved-refresh-tokens.json"。""")
    default List<String> savedUserLogins() {
        return List.of("ThisExampleUsernameShouldBeLongEnoughToNeverBeAnXboxUsername",
            "ThisOtherExampleUsernameShouldAlsoBeLongEnough");
    }

    @Comment("""
            仅适用于正版验证模式。
            指定等待用户授权 Geyser 访问其 Microsoft 账户的秒数。
            在此期间允许用户断开与服务器的连接。""")
    @DefaultNumeric(120)
    int pendingAuthenticationTimeout();

    @Comment("""
            是否提醒控制台和操作员有新的 Geyser 版本支持此 Geyser 版本不支持的 Bedrock 版本。
            建议保持此选项启用，因为许多 Bedrock 平台会自动更新。""")
    @DefaultBoolean(true)
    boolean notifyOnNewBedrockUpdate();

    @Comment("高级配置选项。这些通常不需要修改。")
    AdvancedConfig advanced();

    @Comment("网易专用配置选项")
    NeteaseConfig netease();

    @Comment("""
            bStats 是一个完全匿名的统计追踪器，仅追踪基本信息，
            例如有多少人在线，有多少服务器在使用 Geyser，
            使用什么操作系统等。你可以在此处了解更多关于 bStats 的信息：https://bstats.org/。
            https://bstats.org/plugin/server-implementation/GeyserMC""")
    @DefaultBoolean(true)
    @ExcludePlatform(platforms = {"BungeeCord", "Spigot", "Velocity"}) // 使用的 bStats 平台版本
    boolean enableMetrics();

    @Comment("bStats 指标 uuid。请勿修改！")
    @ExcludePlatform(platforms = {"BungeeCord", "Spigot", "Velocity"}) // 使用的 bStats 平台版本
    default UUID metricsUuid() {
        return UUID.randomUUID();
    }

    @Comment("是否通过控制台发送调试消息")
    boolean debugMode();

    @Comment("请勿修改！")
    @SuppressWarnings("unused")
    default int configVersion() {
        return Constants.CONFIG_VERSION;
    }

    @ConfigSerializable
    interface BedrockConfig extends BedrockListener {
        @Comment("""
                 Geyser 将绑定以监听传入 Bedrock 连接的 IP 地址。
                 通常，仅当您希望限制可以连接到服务器的 IP 时才应更改此项。""")
        @NonNull
        @Override
        @DefaultString("0.0.0.0")
        @AsteriskSerializer.Asterisk
        String address();

        @Comment("""
             Geyser 将监听传入 Bedrock 连接的端口。
             由于 Minecraft: Bedrock Edition 使用 UDP，此端口必须允许 UDP 流量。""")
        @Override
        @DefaultNumeric(19132)
        @NumericRange(from = 0, to = 65535)
        int port();

        @Comment("""
                 某些托管服务每次启动服务器时都会更改您的 Java 端口，并且要求 Bedrock 使用相同的端口。
                 此选项使每次启动服务器时 Bedrock 端口与 Java 端口相同。""")
        @DefaultBoolean
        @PluginSpecific
        boolean cloneRemotePort();

        void address(String address);
        void port(int port);

        @Exclude
        @Override
        default int broadcastPort() {
            return GeyserImpl.getInstance().config().advanced().bedrock().broadcastPort();
        }

        @Exclude
        @Override
        default String primaryMotd() {
            return GeyserImpl.getInstance().config().motd().primaryMotd();
        }

        @Exclude
        @Override
        default String secondaryMotd() {
            return GeyserImpl.getInstance().config().motd().secondaryMotd();
        }

        @Exclude
        @Override
        default String serverName() {
            return GeyserImpl.getInstance().config().gameplay().serverName();
        }
    }

    @ConfigSerializable
    interface JavaConfig extends RemoteServer {
        void address(String address);
        void port(int port);

        @Comment("""
                Bedrock 玩家登录 Java 服务器时将根据何种验证类型进行检查。
                可以是 "floodgate"（参见 https://wiki.geysermc.org/floodgate/）、"online" 或 "offline"。""")
        @NonNull
        @Override
        default AuthType authType() {
            return AuthType.ONLINE;
        }

        void authType(AuthType authType);
        boolean forwardHostname();

        @Override
        @Exclude
        default String minecraftVersion() {
            return GameProtocol.getJavaMinecraftVersion();
        }

        @Override
        @Exclude
        default int protocolVersion() {
            return GameProtocol.getJavaProtocolVersion();
        }

        @Override
        @Exclude
        default boolean resolveSrv() {
            return false;
        }
    }

    @ConfigSerializable
    interface MotdConfig {
        @Comment("""
            将广播给 Minecraft: Bedrock Edition 客户端的 MOTD。如果 "passthrough-motd" 设置为 true，则此项无效。
            如果其中任一为空，则相应的字符串将默认为 "Geyser\"""")
        @DefaultString("Geyser")
        String primaryMotd();
        @DefaultString("Another Geyser server.")
        String secondaryMotd();

        @Comment("Geyser 是否应将 Java 服务器的 MOTD 中继给 Bedrock 玩家。")
        @DefaultBoolean(true)
        boolean passthroughMotd();

        @Comment("""
            可以连接的最大玩家数量。
            这仅是视觉上的，并且仅在禁用 passthrough-motd 时应用。""")
        @DefaultNumeric(100)
        int maxPlayers();

        @Comment("是否将 Java 服务器的玩家数量和最大玩家数中继给 Bedrock 玩家。")
        @DefaultBoolean(true)
        boolean passthroughPlayerCounts();

        @Comment("""
            是否使用服务器 API 方法来确定 Java 服务器的 MOTD 和 ping 直通。
            除非您的 MOTD 或玩家数量显示不正确，否则无需禁用此项。""")
        @DefaultBoolean(true)
        @PluginSpecific
        boolean integratedPingPassthrough();

        @Comment("刷新 MOTD 和玩家数量以 ping Java 服务器的频率，单位为秒。")
        @DefaultNumeric(3)
        int pingPassthroughInterval();
    }

    @ConfigSerializable
    interface GameplayConfig {

        @Comment("将发送给 Minecraft: Bedrock Edition 客户端的服务器名称。这在暂停菜单和设置菜单中都可见。")
        @DefaultString("Geyser")
        String serverName();

        @Comment("""
            允许发送假的冷却指示器。否则 Bedrock 玩家看不到冷却，因为他们仍然使用 1.8 战斗系统。
            请注意：如果启用了冷却，某些用户可能会在冷却序列期间看到黑框，如下所示：
            https://geysermc.org/img/external/cooldown_indicator.png
            可以通过进入 Bedrock 设置中的辅助功能选项卡并将"文本背景不透明度"设置为 0 来禁用它。
            此设置可以设置为 "title"、"actionbar" 或 "disabled\"""")
        default CooldownUtils.CooldownType showCooldown() {
            return CooldownUtils.CooldownType.TITLE;
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        @Comment("""
            Bedrock 客户端在首次打开命令提示符时如果收到大量命令可能会冻结。
            禁用它将阻止发送命令建议并解决 Bedrock 客户端的冻结问题。""")
        @DefaultBoolean(true)
        boolean commandSuggestions();

        @Comment("控制是否向玩家显示坐标。")
        @DefaultBoolean(true)
        boolean showCoordinates();

        @Comment("是否阻止 Bedrock 玩家进行其脚手架式搭桥。")
        boolean disableBedrockScaffolding();

        @Comment("""
            Bedrock 阻止在下界 Y127 以上建造和显示方块。
            此配置选项通过将下界维度 ID 更改为末地 ID 来解决此问题。
            这样做的主要缺点是整个下界将具有相同的红色雾霾，而不是每个生物群系具有不同的雾霾。""")
        boolean netherRoofWorkaround();

        @Comment("""
            是否向其他 Bedrock 版玩家显示 Bedrock 版表情。
            """)
        @DefaultBoolean(true)
        boolean emotesEnabled();

        @Comment("""
            用于标记 Bedrock 玩家物品栏中不可用槽位的物品。例如创造模式下的 2x2 合成网格，
            或大小与通常的 3x9 不同的自定义物品栏菜单。屏障块是默认物品。
            此配置选项可以设置为任何 Bedrock 物品标识符。如果您想将其设置为自定义物品，请确保按以下格式指定物品："geyser_custom:<mapping-name>"
            """)
        @DefaultString("minecraft:barrier")
        String unusableSpaceBlock();

        @Comment("""
            是否添加任何通常不存在于 Bedrock 版中的物品和方块。
            仅当使用不使用"传输包"式服务器切换的代理时才需要禁用它。
            如果禁用，漏斗矿车物品将被映射到漏斗矿车物品。
            Geyser 的方块、物品和头颅映射系统也将被禁用。
            此选项需要重新启动 Geyser 才能更改其设置。""")
        @DefaultBoolean(true)
        boolean enableCustomContent();

        @Comment("""
            如果有任何资源包，强制客户端加载所有资源包。
            如果设置为 false，即使用户不想下载资源包，也允许其连接到服务器。""")
        @DefaultBoolean(true)
        boolean forceResourcePacks();

        @Comment("""
            是否自动提供一个资源包，该资源包是某些 Geyser 功能所必需的，提供给所有连接的 Bedrock 玩家。
            如果启用，force-resource-packs 将被启用。""")
        @DefaultBoolean(true)
        boolean enableIntegratedPack();

        @Comment("""
            是否将玩家 ping 转发到服务器。虽然启用此功能将使 Bedrock 玩家拥有更准确的
            ping，但也可能导致玩家更容易超时。""")
        boolean forwardPlayerPing();

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        @Comment("""
            允许解锁 Xbox 成就。
            如果玩家输入未知命令，他们会收到一条消息，指出作弊已禁用。
            否则，命令按预期工作。""")
        boolean xboxAchievementsEnabled();

        @Comment("""
            每个玩家最多显示的自定义头颅数量。增加此数量可能会降低较弱设备的性能。
            值为 0 将禁用所有自定义头颅。
            将此设置为 -1 将导致显示所有自定义头颅，无论距离或数量如何。""")
        @DefaultNumeric(128)
        int maxVisibleCustomSkulls();

        @Comment("玩家周围显示自定义头颅的半径范围，以方块为单位。")
        @DefaultNumeric(32)
        int customSkullRenderDistance();
    }

    @ConfigSerializable
    interface AdvancedBedrockConfig {
        @Comment("""
                向 Bedrock 客户端广播 MOTD 的端口，告知他们应使用该端口连接到服务器。
                值为 0 将广播上面指定的端口。
                除非 Geyser 运行在与用于连接的端口不同的端口上，否则请勿更改此项。""")
        @DefaultNumeric(0)
        @NumericRange(from = 0, to = 65535)
        int broadcastPort();

        void broadcastPort(int port);

        @Comment("""
                压缩到 Bedrock 客户端的网络流量的程度。数字越大，使用的 CPU 越多，
                但使用的带宽越小。低于 -1 或高于 9 无效。设置为 -1 以禁用。""")
        @DefaultNumeric(6)
        @NumericRange(from = -1, to = 9)
        int compressionLevel();

        @Comment("""
                是否期望连接 Bedrock 客户端使用 HAPROXY 协议。
                这仅在您在 Geyser 实例前面运行 UDP 反向代理时有用。
                如果您不知道这是什么，请不要修改！""")
        @DefaultBoolean
        boolean useHaproxyProtocol();

        @Comment("""
                允许使用 HAPROXY 协议通信的代理 IP 地址/子网列表。仅在启用 "use-proxy-protocol" 时有效，
                并且仅当您无法使用合适的防火墙时才应使用（对于共享托管提供商等通常如此）。
                将此列表留空表示没有 IP 地址白名单。
                支持 IP 地址、子网和指向纯文本文件的链接。""")
        default List<String> haproxyProtocolWhitelistedIps() {
            return Collections.emptyList();
        }

        @Comment("""
            互联网支持的最大 MTU 为 1492，但可能会导致数据包碎片问题。
            默认值为 1400。""")
        @DefaultNumeric(1400)
        int mtu();

        @Comment("""
            此选项禁用 Geyser 为连接 Bedrock 玩家执行的身份验证步骤。
            可用于允许来自 ProxyPass 和 WaterdogPE 的连接。在这些情况下，请确保用户
            不能直接连接到此 Geyser 实例。请参阅 https://www.spigotmc.org/wiki/firewall-guide/ 获取
            帮助 - 并使用 UDP 而不是 TCP。
            对于其他用例禁用 Bedrock 身份验证是不受支持的，因为它允许任何人伪造用户名，因此存在安全风险。
            当禁用此选项时，所有 Floodgate 功能（包括皮肤上传和账户关联）也将无法使用。""")
        @DefaultBoolean(true)
        boolean validateBedrockLogin();
    }

    @ConfigSerializable
    interface AdvancedJavaConfig {
        @Comment("""
                连接到 Java 服务器时是否启用 HAPROXY 协议。
                这仅在以下情况下有用：
                1) 您的 Java 服务器支持 HAPROXY 协议（它可能不支持）
                2) 您在代理的主配置中启用了该选项的情况下运行 Velocity 或 BungeeCord。
                如果您不知道这是什么，请不要修改！""")
        boolean useHaproxyProtocol();

        @Comment("""
        是否直接连接到 Java 服务器，而不创建 TCP 连接。
        仅当与数据包或网络交互的插件无法与 Geyser 正常工作时才应禁用它。
        如果启用，则忽略远程地址和端口部分。
        如果禁用，预计性能会下降，延迟会增加。
        """)
        @DefaultBoolean(true)
        @PluginSpecific
        boolean useDirectConnection();

        @Comment("""
        Geyser 是否应尝试为 Bedrock 玩家禁用（从 Java 服务器到 Geyser 的）数据包压缩。
        这应该是有益的，因为当 Java 数据包不通过网络处理时，无需压缩数据。
        这要求 use-direct-connection 为 true。
        """)
        @DefaultBoolean(true)
        @PluginSpecific
        boolean disableCompression();
    }

    @ConfigSerializable
    interface AdvancedConfig {
        @Comment("""
            指定玩家皮肤图像将缓存到磁盘的天数，以节省从互联网下载它们的时间。
            值为 0 表示禁用。（默认值：0）""")
        int cacheImages();

        @Comment("""
            Geyser 在每个记分板数据包后更新记分板，但是当 Geyser 尝试处理
            每秒大量的记分板数据包时，这可能会导致严重的延迟。
            此选项允许您指定每秒超过多少个记分板数据包后，
            记分板更新将限制为每秒四次更新。""")
        @DefaultNumeric(20)
        int scoreboardPacketThreshold();

        @Comment("""
            Geyser 是否应在命令建议中发送队伍名称。
            如果您使用了大量不需要作为建议的队伍，请禁用此项。""")
        @DefaultBoolean(true)
        boolean addTeamSuggestions();

        @Comment("""
            要发送给 Bedrock 客户端供下载的远程资源包 URL 列表。
            Bedrock 客户端对这些资源的交付方式非常挑剔 - 请参阅我们的 wiki 页面了解更多信息：https://geysermc.org/wiki/geyser/packs/
            """)
        default List<String> resourcePackUrls() {
            return Collections.emptyList();
        }

        // 暂时不能是 File 类型，因为我们可能希望在插件实例中隐藏它。
        @Comment("""
            Floodgate 使用加密来确保来自授权来源的使用。
            这应该指向 Floodgate（BungeeCord、Spigot 或 Velocity）生成的公钥。
            如果不使用 Floodgate，可以忽略此项。
            如果您在同一服务器上使用 Floodgate 的插件版本，该密钥将自动从 Floodgate 获取。""")
        @DefaultString("key.pem")
        String floodgateKeyFile();

        @Comment("用于 Geyser 到 Java 服务器连接的高级网络选项")
        AdvancedJavaConfig java();

        @Comment("用于 Geyser 的 Bedrock 监听器的高级网络选项")
        AdvancedBedrockConfig bedrock();
    }

    @ConfigSerializable
    interface ShopConfig {
        @DefaultString("")
        String gameId();

        @Comment("正式服签名")
        @DefaultString("")
        String gameKey();

        @DefaultString("")
        @Comment("测试服签名")
        String testGameKey();

        @Comment("是否是测试服")
        @DefaultBoolean(true)
        boolean isTestServer();

        @Comment("商城URL，一般不需要配置，预留用")
        @DefaultString("")
        String shopServerUrl();

        @Comment("服务URL，一般不需要配置，预留用")
        @DefaultString("")
        String webServerUrl();
    }


    @ConfigSerializable
    interface ServiceConfig {
        @DefaultString("http://skinsync.bjd-mc.com:12455")
        String skinurl();

        @DefaultString("114514")
        String token();
    }

    @ConfigSerializable
    interface OptionalPacksConfig {
        @DefaultBoolean(false)
        boolean enableOptionalPacks();

        @DefaultString("jdbc:mariadb://127.0.0.1:3306/minecraft?allowPublicKeyRetrieval=true&useSSL=false")
        String mysqlUrl();

        @DefaultString("minecraft")
        String mysqlUser();

        @DefaultString("minecraft")
        String mysqlPass();
    }

    @ConfigSerializable
    interface RedisConfig {
        @DefaultString("127.0.0.1")
        String url();

        @DefaultNumeric(6379)
        int port();

        @DefaultString("mcnetgame")
        String password();
    }

    @ConfigSerializable
    interface NeteaseConfig {
        @Comment("是否需要网易正版验证。")
        @DefaultBoolean(false)
        boolean onlineMode();

        @Comment("是否允许 PC 客户端。")
        @DefaultBoolean(false)
        boolean allowedPc();

        @Comment("是否接受自定义 Bedrock 几何模型。")
        @DefaultBoolean(false)
        boolean allowCustomGeometry();

        @Comment("网易商店")
        ShopConfig shop();

        @Comment("皮肤同步服务设置")
        ServiceConfig service();

        @Comment("可选资源包设置")
        OptionalPacksConfig optionalPacks();

        @Comment("Redis 设置")
        RedisConfig redis();
    }
}
