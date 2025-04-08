package com.ujcms.cms.ext.component;

import com.ujcms.cms.ext.service.ImportDataService;
import com.ujcms.cms.ext.service.args.ImportDataArgs;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.service.args.ModelArgs;
import com.ujcms.cms.ext.domain.ImportData;
import com.ujcms.commons.db.tree.TreeMoveType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static com.ujcms.commons.db.DataMigration.getColumns;

/**
 * 数据导入组件
 *
 * @author PONY
 */
@Component
public class ImportDataComponent {
    private static final Logger logger = LoggerFactory.getLogger(ImportDataComponent.class);

    private final ModelService modelService;
    private final ChannelService channelService;
    private final ImportDataService service;

    public ImportDataComponent(ModelService modelService, ChannelService channelService, ImportDataService service) {
        this.modelService = modelService;
        this.channelService = channelService;
        this.service = service;
    }

    public void connectionTest(String url, String username, String password, String driverClassName)
            throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
        dataSource.setDriverClassName(driverClassName);
        try (Connection ignored = dataSource.getConnection(username, password)) {
            logger.debug("Connection success!");
        }
    }

    public void importChannel(String url, String username, String password, String driverClassName,
                              String sql, Long siteId) throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
        dataSource.setDriverClassName(driverClassName);
        Model channelModel = modelService.selectList(ModelArgs.of().scopeSiteId(siteId).type(Channel.MODEL_TYPE))
                .iterator().next();
        Model articleModel = modelService.selectList(ModelArgs.of().scopeSiteId(siteId).type(Article.MODEL_TYPE))
                .iterator().next();
        try (Connection conn = dataSource.getConnection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            final List<String> columns = getColumns(rs);
            while (rs.next()) {
                service.insertChannel(rs, columns, siteId, channelModel.getId(), articleModel.getId());
            }
        }
    }

    public void handleChannelParentId() {
        final ImportDataArgs args = ImportDataArgs.of()
                .tableName(ImportData.TABLE_CHANNEL).type(ImportData.TYPE_MIGRATION);
        final List<ImportData> list = service.selectList(args);
        for (ImportData importData : list) {
            final String origParentId = importData.getOrigParentId();
            if (origParentId != null) {
                final Channel channel = channelService.select(Long.parseLong(importData.getCurrentId()));
                final Channel parent = Optional.ofNullable(service.selectByOrigId(origParentId))
                        .map(ImportData::getCurrentId)
                        .map(Long::parseLong)
                        .map(channelService::select)
                        .orElse(null);
                if (channel != null && parent != null) {
                    channelService.move(channel, parent, TreeMoveType.INNER, channel.getSiteId());
                }
            }
        }
    }

    public void importArticle(String url, String username, String password, String driverClassName,
                              String sql, Site site, User user) throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
        dataSource.setDriverClassName(driverClassName);
        Channel channel = channelService.listBySiteId(site.getId()).iterator().next();
        try (Connection conn = dataSource.getConnection(username, password);
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            final List<String> columns = getColumns(rs);
            while (rs.next()) {
                service.insertArticle(rs, columns, site.getId(), site.getOrgId(), user.getId(), channel.getId());
            }
        }
    }

    public void deleteCorrespond() {
        service.deleteByType(ImportData.TYPE_MIGRATION);
    }
}
