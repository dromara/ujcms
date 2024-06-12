package com.ujcms.cms.core.support;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.base.RoleBase;
import org.flowable.engine.form.FormProperty;
import org.flowable.engine.form.TaskFormData;

import java.util.*;
import java.util.stream.Collectors;


/**
 * 审核工具类
 *
 * @author PONY
 */
public class FlowableUtils {

    public static final String PROCESS_VARIABLE_ORG_ID = "processOrgId";
    public static final String PROCESS_VARIABLE_USER_ID = "processUserId";
    public static final String PROCESS_VARIABLE_BUSINESS_KEY = "processBusinessKey";

    public static final String COMMENT_TYPE_PASS = "pass";
    public static final String COMMENT_TYPE_REJECT = "reject";
    public static final String COMMENT_TYPE_DELEGATE = "delegate";
    public static final String COMMENT_TYPE_TRANSFER = "transfer";
    public static final String COMMENT_TYPE_BACK = "back";
    public static final String COMMENT_TYPE_ADD_EXECUTION = "addExecution";

    public static final String NR_OF_INSTANCES = "nrOfInstances";
    public static final String NR_OF_ACTIVE_INSTANCES = "nrOfActiveInstances";
    public static final String NR_OF_COMPLETED_INSTANCES = "nrOfCompletedInstances";

    public static final String NR_OF_APPROVED = "nrOfApproved";
    public static final String NR_OF_REJECTED = "nrOfRejected";

    public static final String MULTI_INSTANCE_REJECTED = "multiInstanceRejected";

    public static Set<String> getCandidateGroups(User user) {
        Set<String> candidateGroups = user.getRoleList().stream().map(RoleBase::getId).map(String::valueOf)
                .collect(Collectors.toSet());
        candidateGroups.addAll(user.fetchAllOrgIds().stream().map(orgId -> "org:" + orgId).collect(Collectors.toSet()));
        return candidateGroups;
    }

    public static Map<String, String> getFormDefaultProperties(TaskFormData formData) {
        List<FormProperty> formProperties = formData.getFormProperties();
        if (formProperties.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> properties = new HashMap<>(16);
        for (FormProperty property : formProperties) {
            if (property.getValue() != null) {
                properties.put(property.getId(), property.getValue());
            }
        }
        return properties;

    }

    public static int getInt(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        return 0;
    }

    public static boolean getBoolean(Object obj) {
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        return false;
    }

    private FlowableUtils() {
    }
}
