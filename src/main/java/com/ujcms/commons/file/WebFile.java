package com.ujcms.commons.file;

import com.ujcms.commons.image.Images;
import com.ujcms.commons.web.UrlBuilder;
import io.minio.ObjectStat;
import io.minio.messages.Item;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.lang.Nullable;

import java.io.File;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.ujcms.commons.file.FilesEx.SLASH;

/**
 * WebFile
 *
 * @author liufang
 */
public class WebFile {
    private final String id;
    private boolean directory;
    private long length;
    @Nullable
    private OffsetDateTime lastModified;
    private String text = "";

    private final String displayPath;

    public WebFile(String id) {
        this(id, "");
    }

    public WebFile(String id, String displayPath) {
        this.id = id;
        this.displayPath = displayPath;
    }

    public WebFile(String id, boolean directory) {
        this(id, "", directory);
    }

    public WebFile(String id, String displayPath, boolean directory) {
        this.id = id;
        this.displayPath = displayPath;
        this.directory = directory;
    }

    public WebFile(String id, String displayPath, File file) {
        this.id = id;
        this.displayPath = displayPath;
        this.fillWithFile(file);
    }

    public WebFile(String id, String displayPath, FTPFile file) {
        this.id = id;
        this.displayPath = displayPath;
        this.fillWithFtp(file);
    }

    public WebFile(String id, String displayPath, ObjectStat stat) {
        this.id = id;
        this.displayPath = displayPath;
        this.fillWithMinIoStat(stat);
    }

    public WebFile(String id, String displayPath, Item item) {
        this.id = id;
        this.displayPath = displayPath;
        this.fillWithMinIoItem(item);
    }

    public void fillWithFile(File file) {
        this.setDirectory(file.isDirectory());
        this.setLastModified(OffsetDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()),
                ZoneId.systemDefault()));
        this.setLength(file.length());
    }

    public void fillWithFtp(FTPFile file) {
        this.setDirectory(file.isDirectory());
        this.setLastModified(OffsetDateTime.ofInstant(Instant.ofEpochMilli(file.getTimestamp().getTimeInMillis()),
                ZoneId.systemDefault()));
        this.setLength(file.getSize());
    }

    public void fillWithMinIoStat(ObjectStat stat) {
        this.setDirectory(this.id.endsWith(SLASH));
        this.setLastModified(stat.createdTime().toOffsetDateTime());
        this.setLength(stat.length());
    }

    public void fillWithMinIoItem(Item item) {
        this.setDirectory(this.id.endsWith(SLASH));
        // 在minio中获取目录的lastModified()会报空指针异常
        if (!isDirectory()) {
            this.setLastModified(item.lastModified().toOffsetDateTime());
        }
        this.setLength(item.size());
    }

    public String getId() {
        if (StringUtils.isBlank(this.id)) {
            return "/";
        }
        return this.id;
    }

    public String getName() {
        if (id.endsWith(SLASH)) {
            return FilenameUtils.getName(id.substring(0, id.length() - 1));
        }
        return FilenameUtils.getName(id);
    }

    public String getOrigName() {
        if (id.endsWith(SLASH)) {
            return getName() + SLASH;
        }
        return getName();
    }

    public String getPath() {
        if (id.endsWith(SLASH)) {
            return FilenameUtils.getPath(id.substring(0, id.length() - 1));
        }
        return FilenameUtils.getPath(id);
    }

    public String getBaseName() {
        if (id.endsWith(SLASH)) {
            return FilenameUtils.getBaseName(id.substring(0, id.length() - 1));
        }
        return FilenameUtils.getBaseName(id);
    }

    public String getExtension() {
        if (id.endsWith(SLASH)) {
            return FilenameUtils.getExtension(id.substring(0, id.length() - 1));
        }
        return FilenameUtils.getExtension(id);
    }

    public String getParentId() {
        int index = getId().lastIndexOf(SLASH, 1);
        if (index < 0) {
            return "";
        }
        return getId().substring(0, index);
    }

    public boolean isFile() {
        return !directory;
    }

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public String getSize() {
        return FilesEx.getSize(this.length);
    }

    @Nullable
    public OffsetDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(@Nullable OffsetDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public FileType getFileType() {
        if (isDirectory()) {
            return FileType.DIRECTORY;
        }
        String extension = getExtension();
        if (Images.isImageExtension(extension)) {
            return FileType.IMAGE;
        } else if (StringUtils.equalsAnyIgnoreCase(extension, TEXT_EXTENSIONS.toArray(new String[0]))) {
            return FileType.TEXT;
        } else if (StringUtils.equalsIgnoreCase(extension, ZIP_EXTENSION)) {
            return FileType.ZIP;
        } else {
            return FileType.FILE;
        }
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getUrl() {
        return UrlBuilder.of(displayPath).appendPath(getId()).toString();
    }

    public boolean isEditable() {
        return getFileType() == FileType.TEXT;
    }

    public boolean isImage() {
        return getFileType() == FileType.IMAGE;
    }

    public boolean isZip() {
        return getFileType() == FileType.ZIP;
    }

    public enum FileType {
        /**
         * 文件夹
         */
        DIRECTORY,
        /**
         * ZIP文件
         */
        ZIP,
        /**
         * 文本文件
         */
        TEXT,
        /**
         * 图片
         */
        IMAGE,
        /**
         * 文件
         */
        FILE
    }

    public static final Comparator<WebFile> NAME_ASC = (file1, file2) -> {
        if (file1.isDirectory() && !file2.isDirectory()) {
            return -1;
        } else if (!file1.isDirectory() && file2.isDirectory()) {
            return 1;
        }
        String name1 = file1.getName().toLowerCase();
        String name2 = file2.getName().toLowerCase();
        return name1.compareTo(name2);
    };
    public static final Comparator<WebFile> NAME_DESC = (file1, file2) -> {
        if (file1.isDirectory() && !file2.isDirectory()) {
            return 1;
        } else if (!file1.isDirectory() && file2.isDirectory()) {
            return -1;
        }
        String name1 = file1.getName().toLowerCase();
        String name2 = file2.getName().toLowerCase();
        return name2.compareTo(name1);
    };
    public static final Comparator<WebFile> LAST_MODIFIED_ASC = (file1, file2) -> {
        if (file1.isDirectory() || file1.getLastModified() == null) {
            return -1;
        }
        if (file2.isDirectory() || file2.getLastModified() == null) {
            return 1;
        }
        long lastModified1 = file1.getLastModified().toInstant().toEpochMilli();
        long lastModified2 = file2.getLastModified().toInstant().toEpochMilli();
        return (int) (lastModified1 - lastModified2);
    };
    public static final Comparator<WebFile> LAST_MODIFIED_DESC = (file1, file2) -> {
        if (file1.isDirectory() || file1.getLastModified() == null) {
            return -1;
        }
        if (file2.isDirectory() || file2.getLastModified() == null) {
            return 1;
        }
        long lastModified1 = file1.getLastModified().toInstant().toEpochMilli();
        long lastModified2 = file2.getLastModified().toInstant().toEpochMilli();
        return -(int) (lastModified1 - lastModified2);
    };
    public static final Comparator<WebFile> FILE_TYPE_ASC = (file1, file2) -> {
        if (file1.isDirectory() && !file2.isDirectory()) {
            return -1;
        } else if (!file1.isDirectory() && file2.isDirectory()) {
            return 1;
        } else if (file1.isDirectory() && file2.isDirectory()) {
            String name1 = file1.getName().toLowerCase();
            String name2 = file2.getName().toLowerCase();
            return name1.compareTo(name2);
        }
        FileType type1 = file1.getFileType();
        FileType type2 = file2.getFileType();
        int num = type1.compareTo(type2);
        if (num == 0) {
            String ext1 = file1.getExtension();
            String ext2 = file2.getExtension();
            num = ext1.compareTo(ext2);
        }
        return num;
    };
    public static final Comparator<WebFile> FILE_TYPE_DESC = (file1, file2) -> {
        if (file1.isDirectory() && !file2.isDirectory()) {
            return 1;
        } else if (!file1.isDirectory() && file2.isDirectory()) {
            return -1;
        } else if (file1.isDirectory() && file2.isDirectory()) {
            String name1 = file1.getName().toLowerCase();
            String name2 = file2.getName().toLowerCase();
            return name2.compareTo(name1);
        }
        FileType type1 = file1.getFileType();
        FileType type2 = file2.getFileType();
        int num = type1.compareTo(type2);
        if (num == 0) {
            String ext1 = file1.getExtension();
            String ext2 = file2.getExtension();
            num = ext1.compareTo(ext2);
        }
        return -num;
    };
    public static final Comparator<WebFile> LENGTH_ASC = (file1, file2) -> {
        if (file1.isDirectory() && !file2.isDirectory()) {
            return -1;
        } else if (!file1.isDirectory() && file2.isDirectory()) {
            return 1;
        } else if (file1.isDirectory() && file2.isDirectory()) {
            return file1.getName().compareTo(file2.getName());
        }
        long length1 = file1.getLength();
        long length2 = file2.getLength();
        return (int) (length1 - length2);
    };
    public static final Comparator<WebFile> LENGTH_DESC = (file1, file2) -> {
        if (file1.isDirectory() && !file2.isDirectory()) {
            return 1;
        } else if (!file1.isDirectory() && file2.isDirectory()) {
            return -1;
        } else if (file1.isDirectory() && file2.isDirectory()) {
            return file1.getName().compareTo(file2.getName());
        }
        long length1 = file1.getLength();
        long length2 = file2.getLength();
        return -(int) (length1 - length2);
    };

    public static void sort(List<WebFile> list, String sort, String direction) {
        Comparator<WebFile> comp;
        switch (sort) {
            case "name":
                comp = "desc".equals(direction) ? NAME_DESC : NAME_ASC;
                break;
            case "lastModified":
                comp = "desc".equals(direction) ? LAST_MODIFIED_DESC : LAST_MODIFIED_ASC;
                break;
            case "fileType":
                comp = "desc".equals(direction) ? FILE_TYPE_DESC : FILE_TYPE_ASC;
                break;
            case "length":
                comp = "desc".equals(direction) ? LENGTH_DESC : LENGTH_ASC;
                break;
            default:
                comp = NAME_ASC;
                break;
        }
        list.sort(comp);
    }

    public static final List<String> TEXT_EXTENSIONS = Collections.unmodifiableList(
            Arrays.asList("html", "htm", "js", "css", "txt", "xml", "ftl", "vm", "jsp", "jspx",
                    "asp", "aspx", "php", "sql", "tag", "tld", "properties", "yaml", "yml"));
    public static final String ZIP_EXTENSION = "zip";
}