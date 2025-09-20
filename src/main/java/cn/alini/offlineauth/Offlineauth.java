package cn.alini.offlineauth;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Offlineauth.MODID)
public class Offlineauth {

    public static final String MODID = "offlineauth";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Offlineauth() {
        //mod成功加载日志
        LOGGER.info("OfflineAuth mod loaded successfully!");
        new AuthConfig().save();
    }
}
