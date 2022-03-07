package com.ujcms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ujcms.core.domain.Attachment;
import com.ujcms.core.domain.AttachmentRefer;
import com.ujcms.core.mapper.AttachmentMapper;
import com.ujcms.core.mapper.AttachmentReferMapper;
import com.ofwise.util.file.FileHandler;
import com.ofwise.util.query.QueryInfo;
import com.ofwise.util.query.QueryParser;
import com.ofwise.util.web.PathResolver;
import com.ofwise.util.web.Uploads;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 附件 Service
 *
 * @author PONY
 */
@Service
public class AttachmentService {
    private AttachmentMapper mapper;
    private AttachmentReferMapper referMapper;
    private PathResolver pathResolver;

    private SeqService seqService;

    public AttachmentService(AttachmentMapper mapper, AttachmentReferMapper referMapper, PathResolver pathResolver,
                             SeqService seqService) {
        this.mapper = mapper;
        this.referMapper = referMapper;
        this.pathResolver = pathResolver;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertRefer(String referType, Integer referId, List<String> urls) {
        Set<Integer> ids = doInsertRefer(referType, referId, urls);
        if (!ids.isEmpty()) {
            mapper.updateUsed(ids);
        }
    }

    private Set<Integer> doInsertRefer(String referType, Integer referId, List<String> urls) {
        Set<Integer> ids = new HashSet<>();
        urls.forEach(url -> {
            Attachment attachment = mapper.findByUrl(url);
            if (attachment != null) {
                if (referMapper.select(attachment.getId(), referType, referId) == null) {
                    referMapper.insert(new AttachmentRefer(attachment.getId(), referType, referId));
                    ids.add(attachment.getId());
                }
            }
        });
        return ids;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRefer(String referType, Integer referId, List<String> urls) {
        Set<Integer> ids = findAttachmentIds(referType, referId);
        doDeleteRefer(referType, referId);
        if (!urls.isEmpty()) {
            ids.addAll(doInsertRefer(referType, referId, urls));
        }
        if (!ids.isEmpty()) {
            mapper.updateUsed(ids);
        }
    }

    private Set<Integer> findAttachmentIds(String referType, Integer referId) {
        Set<Integer> ids = new HashSet<>();
        referMapper.listByReferTypeAndReferId(referType, referId).forEach(refer -> ids.add(refer.getAttachmentId()));
        return ids;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRefer(String referType, Integer referId) {
        Set<Integer> ids = findAttachmentIds(referType, referId);
        doDeleteRefer(referType, referId);
        if (!ids.isEmpty()) {
            mapper.updateUsed(ids);
        }
    }

    private void doDeleteRefer(String referType, Integer referId) {
        referMapper.deleteByReferTypeAndReferId(referType, referId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Attachment bean) {
        bean.setId(seqService.getNextVal(Attachment.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Attachment bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        Attachment bean = select(id);
        if (bean != null && !bean.getUsed()) {
            FileHandler fileHandler = bean.getSite().getStorage().getFileHandler(pathResolver);
            Optional.ofNullable(fileHandler.getName(bean.getUrl())).ifPresent(pathname -> {
                fileHandler.delete(pathname);
                // 删除缩略图，如果有的话
                fileHandler.delete(Uploads.getThumbnailName(pathname));
            });
        }
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Attachment select(Integer id) {
        return mapper.select(id);
    }

    public List<Attachment> selectList(@Nullable Map<String, Object> queryMap) {
        QueryInfo queryInfo = QueryParser.parse(queryMap, Attachment.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Attachment> selectList(@Nullable Map<String, Object> queryMap,
                                       @Nullable Integer offset, @Nullable Integer limit) {
        return PageHelper.offsetPage(offset == null ? 0 : offset, limit == null ? Integer.MAX_VALUE : limit, false)
                .doSelectPage(() -> selectList(queryMap));
    }

    public Page<Attachment> selectPage(@Nullable Map<String, Object> queryMap, int page, int pageSize) {
        return PageHelper.startPage(page, pageSize).doSelectPage(() -> selectList(queryMap));
    }
}