package com.ujcms.generator;

import com.ujcms.generator.mybatis.MyBatisGenerator;
import org.mybatis.generator.config.Configuration;
import org.mybatis.generator.config.xml.ConfigurationParser;
import org.mybatis.generator.exception.InvalidConfigurationException;
import org.mybatis.generator.exception.XMLParserException;
import org.mybatis.generator.internal.DefaultShellCallback;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CodeGenerator {
    public static void main(String[] args) throws Exception {
        generate("src/main/resources/generatorCoreConfig.xml");
        generate("src/main/resources/generatorExtConfig.xml");
        // 商业版整栈独占的表，生成到 ujcms-commercial。配置文件只随商业版发行，开源版（社区版）不包含，不存在则跳过
        // 标准版（T2）：定时任务
        generateIfPresent("src/main/resources/generatorCommercialT2Config.xml");
        // 高级版（T3）：微信公众号（2026-09-08 起由 T2 升至 T3）
        generateIfPresent("src/main/resources/generatorCommercialT3Config.xml");
    }

    /** 配置文件存在才生成：商业版配置只随商业版发行，开源版（社区版）不包含。 */
    private static void generateIfPresent(String configPath)
            throws XMLParserException, IOException, InvalidConfigurationException, SQLException, InterruptedException {
        if (!new File(configPath).isFile()) {
            System.out.println("[CodeGenerator] 跳过 " + configPath + "：文件不存在（商业版配置仅随商业版发行）");
            return;
        }
        generate(configPath);
    }

    private static void generate(String configPath)
            throws XMLParserException, IOException, InvalidConfigurationException, SQLException, InterruptedException {
        List<String> warnings = new ArrayList<>();
        File configFile = new File(configPath);
        ConfigurationParser cp = new ConfigurationParser(warnings);
        Configuration config = cp.parseConfiguration(configFile);
        DefaultShellCallback callback = new DefaultShellCallback(false);
        MyBatisGenerator generator = new MyBatisGenerator(config, callback, warnings);
        generator.generate(null);
    }
}
