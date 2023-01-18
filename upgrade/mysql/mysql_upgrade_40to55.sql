INSERT INTO ujcms_dict_type (id_, name_, alias_, scope_, order_, sys_) VALUES (10, '留言类型', 'sys_message_board_type', 2, 32767, 1);

INSERT INTO ujcms_org (id_, parent_id_, name_, phone_, address_, contacts_, depth_, order_) VALUES (0,NULL,'前台组织',NULL,NULL,NULL,1,1);
INSERT INTO ujcms_user (id_, group_id_, org_id_, username_, password_, email_, mobile_, alias_, nickname_, avatar_, rank_, status_, type_, password_modified_) VALUES (0,1,0,'anonymous','0',NULL,NULL,NULL,NULL,NULL,0,2,5,'2022-07-11 12:53:23');
INSERT INTO ujcms_user_ext (id_, real_name_, gender_, birthday_, location_, bio_, created_, login_date_, login_ip_, login_count_, error_date_, error_count_, history_password_) VALUES (0,NULL,'m',NULL,NULL,NULL,'2021-03-18 12:25:06','2021-03-18 12:25:06','127.0.0.1',0,'2021-03-18 12:25:06',0,NULL);

insert into ujcms_org_tree(ancestor_id_, descendant_id_) values (0,0);

update ujcms_article_image set name_ = '' where name_ is null;
update ujcms_article_image set description_ = '' where description_ is null;
update ujcms_article_ext u set u.image_list_json_ = (select CONCAT('[',GROUP_CONCAT(CONCAT('{"name":"',t.name_,'","description":"',t.description_,'","url":"',t.url_,'"}') ORDER BY t.order_,t.id_ SEPARATOR ','),']') from ujcms_article_image t where t.article_id_ = u.id_);
delete from ujcms_article_image;

update ujcms_article_file set name_ = '' where name_ is null;
update ujcms_article_ext u set u.file_list_json_ = (select CONCAT('[',GROUP_CONCAT(CONCAT('{"name":"',t.name_,'","url":"',t.url_,'","length":',t.length_,'}') ORDER BY t.order_,t.id_ SEPARATOR ','),']') from ujcms_article_file t where t.article_id_ = u.id_);
delete from ujcms_article_file;
