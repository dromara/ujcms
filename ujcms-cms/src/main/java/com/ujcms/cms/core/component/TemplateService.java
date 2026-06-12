package com.ujcms.cms.core.component;

import com.ujcms.cms.core.support.Constants;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author PONY
 */
@Component
public class TemplateService {
    private final ResourceLoader resourceLoader;

    public TemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public List<String> getTemplates(String theme, String startWitch) throws IOException {
        List<String> themeList = new ArrayList<>();
        File file = resourceLoader.getResource(theme).getFile();
        if (!file.exists()) {
            return themeList;
        }
        File[] themeFiles = file.listFiles((dir, name) ->
                name.startsWith(startWitch) && name.endsWith(Constants.TEMPLATE_SUFFIX));
        if (themeFiles == null) {
            return themeList;
        }
        for (File themeFile : themeFiles) {
            String name = themeFile.getName();
            themeList.add(themeFile.getName().substring(0, name.indexOf(Constants.TEMPLATE_SUFFIX)));
        }
        return themeList;
    }
}
