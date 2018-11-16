package script.db


databaseChangeLog(logicalFilePath: 'dba/devops_cluster.groovy') {
    changeSet(author: 'Younger', id: '2018-11-01-create-table') {
        createTable(tableName: "devops_cluster", remarks: 'cluster information') {
            column(name: 'id', type: 'BIGINT UNSIGNED', remarks: '主键，ID', autoIncrement: true) {
                constraints(primaryKey: true)
            }
            column(name: 'organization_id', type: 'BIGINT UNSIGNED', remarks: '组织id')
            column(name: 'name', type: 'VARCHAR(64)', remarks: '集群名字')
            column(name: 'code', type: 'VARCHAR(64)', remarks: '集群编码')
            column(name: 'description', type: 'VARCHAR(64)', remarks: '集群描述')
            column(name: 'token', type: 'VARCHAR(64)', remarks: '集群token')
            column(name: 'skip_check_project_permission', type: 'TINYINT UNSIGNED', remarks: '是否跳过项目权限校验')
            column(name: "object_version_number", type: "BIGINT UNSIGNED", defaultValue: "1")
            column(name: "created_by", type: "BIGINT UNSIGNED", defaultValue: "0")
            column(name: "creation_date", type: "DATETIME", defaultValueComputed: "CURRENT_TIMESTAMP")
            column(name: "last_updated_by", type: "BIGINT UNSIGNED", defaultValue: "0")
            column(name: "last_update_date", type: "DATETIME", defaultValueComputed: "CURRENT_TIMESTAMP")
        }

        addUniqueConstraint(tableName: 'devops_cluster',
                constraintName: 'uk_orgId_code', columnNames: 'organization_id,code')
    }




}