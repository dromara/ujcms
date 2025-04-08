package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.ext.component.ImportDataComponent;
import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.exception.Http403Exception;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.sql.SQLException;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;

/**
 * 导入数据 Controller
 *
 * @author PONY
 */
@RestController("backendImportDataController")
@RequestMapping(BACKEND_API + "/ext/import-data")
public class ImportDataController {
    private final ImportDataComponent component;
    private final Props props;

    public ImportDataController(ImportDataComponent component, Props props) {
        this.component = component;
        this.props = props;
    }

    @PostMapping("test-connection")
    @PreAuthorize("hasAnyAuthority('importData:show','*')")
    public ResponseEntity<Body> connectionTest(@RequestBody @Valid DataSourceParams params) throws SQLException {
        validateEnabled();
        component.connectionTest(params.getUrl(), params.getUsername(), params.getPassword(),
                params.getDriverClassName());
        return Responses.ok();
    }

    @PostMapping("import-channel")
    @PreAuthorize("hasAnyAuthority('importData:show','*')")
    @OperationLog(module = "importData", operation = "importChanel", type = OperationType.CREATE)
    public ResponseEntity<Body> importChanel(@RequestBody @Valid DataSourceSqlParams params) throws SQLException {
        validateEnabled();
        component.importChannel(params.getUrl(), params.getUsername(), params.getPassword(),
                params.getDriverClassName(), params.getSql(), Contexts.getCurrentSiteId());
        component.handleChannelParentId();
        return Responses.ok();
    }

    @PostMapping("import-article")
    @PreAuthorize("hasAnyAuthority('importData:show','*')")
    @OperationLog(module = "importData", operation = "importArticle", type = OperationType.CREATE)
    public ResponseEntity<Body> importArticle(@RequestBody @Valid DataSourceSqlParams params) throws SQLException {
        validateEnabled();
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        component.importArticle(params.getUrl(), params.getUsername(), params.getPassword(),
                params.getDriverClassName(), params.getSql(), site, user);
        return Responses.ok();
    }

    @DeleteMapping("delete-correspond")
    @PreAuthorize("hasAnyAuthority('importData:show','*')")
    @OperationLog(module = "importData", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> deleteCorrespond() {
        validateEnabled();
        component.deleteCorrespond();
        return Responses.ok();
    }

    private void validateEnabled() {
        if (!props.isDataMigrationEnabled()) {
            throw new Http403Exception("Data migration not enabled!");
        }
    }

    public static class DataSourceParams implements Serializable {
        private static final long serialVersionUID = 2554535078974462040L;

        @NotBlank
        private String url;
        @NotBlank
        private String username;
        private String password;
        @NotBlank
        private String driverClassName;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }
    }

    public static class DataSourceSqlParams extends DataSourceParams {
        private static final long serialVersionUID = 9024153503089629402L;

        @NotBlank
        private String sql;

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }
    }
}