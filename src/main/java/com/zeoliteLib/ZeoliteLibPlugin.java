package com.zeoliteLib;

import com.zeoliteLib.api.ZeoliteLibApi;
import com.zeoliteLib.api.ZeoliteLibApiImpl;
import com.zeoliteLib.internal.ModuleBootstrap;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZeoliteLibPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // 宿主纪律（06 §5）：enable 唯一动作 = 登记插件实例；零监听器、零模块资源
        ModuleBootstrap.install(this);

        getServer().getServicesManager().register(
                ZeoliteLibApi.class,
                new ZeoliteLibApiImpl(),
                this,
                ServicePriority.Normal
        );
    }

    @Override
    public void onDisable() {
        // 宿主纪律（06 §5）：disable 唯一动作 = 清除登记（幂等）；
        // 模块监听器随插件自动注销（HandlerList），骨架不做额外清理
        ModuleBootstrap.clear();
    }

}
