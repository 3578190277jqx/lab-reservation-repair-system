#!/usr/bin/env bash
# The official MySQL image sources non-executable init scripts after 01-schema.sql.
# This script also supports executable mode by loading its SQL helper explicitly.
# Official helpers intentionally read optional unset variables; do not enable nounset.
set -Eeo pipefail

if ! declare -F docker_process_sql >/dev/null; then
  source /usr/local/bin/docker-entrypoint.sh
  # Executable init scripts run in a child shell and do not inherit SOCKET.
  docker_setup_env mysqld
fi

seed_initial_data() {
  local password_hash="${INITIAL_ADMIN_PASSWORD_HASH:-}"
  local bcrypt_pattern='^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$'

  # Fail before executing SQL. Never write a known password or an invalid hash.
  # The permitted characters also exclude quotes and SQL control characters.
  if [[ ! "$password_hash" =~ $bcrypt_pattern ]]; then
    printf '%s\n' 'ERROR: INITIAL_ADMIN_PASSWORD_HASH must contain a valid 60-character BCrypt hash.' >&2
    return 1
  fi
  if [[ -z "${MYSQL_DATABASE:-}" ]]; then
    printf '%s\n' 'ERROR: MYSQL_DATABASE is required for initial data.' >&2
    return 1
  fi

  # Only new, empty Docker data volumes run this script. These are demonstration
  # laboratories and devices; no records are copied from the developer database.
  # Use the password chosen when running PasswordHashTool to log in as admin.
  docker_process_sql --database="$MYSQL_DATABASE" --default-character-set=utf8mb4 <<SQL
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO \`user\` (id, username, password, real_name, role, status)
VALUES (1, 'admin', '$password_hash', '系统管理员', 'admin', 1);

INSERT INTO lab (id, lab_code, name, location, admin_id, description, status)
VALUES
  (1, 'LAB-COMPUTER-01', '计算机综合实验室', '实验楼 A301', 1, '程序设计、网络与综合实践实验室', 1),
  (2, 'LAB-ELECTRONICS-01', '电子技术实验室', '实验楼 B201', 1, '模拟电路、数字电路与电子技术实验室', 1),
  (3, 'LAB-PHYSICS-01', '基础物理实验室', '实验楼 C101', 1, '大学物理与基础测量实验室', 1);

INSERT INTO equipment (id, lab_id, equipment_code, name, model, manufacturer, status, purchase_date, description)
VALUES
  (1, 1, 'EQ-PC-001', '实验工作站一号', '教学工作站', '通用设备', 0, '2025-09-01', '用于程序设计与软件开发实验'),
  (2, 1, 'EQ-NET-001', '网络实验套件', '网络教学套件', '通用设备', 0, '2025-09-01', '用于交换机与路由配置实验'),
  (3, 2, 'EQ-OSC-001', '数字示波器', '双通道示波器', '通用设备', 0, '2025-09-01', '用于电子信号观察与分析'),
  (4, 2, 'EQ-SIGNAL-001', '函数信号发生器', '基础信号发生器', '通用设备', 0, '2025-09-01', '用于基础电路信号输入实验'),
  (5, 3, 'EQ-OPTICS-001', '光学实验平台', '基础光学平台', '通用设备', 0, '2025-09-01', '用于几何光学与波动光学实验'),
  (6, 3, 'EQ-METER-001', '数字万用表', '台式数字万用表', '通用设备', 0, '2025-09-01', '用于电压、电流和电阻测量实验');

COMMIT;
SQL
}

seed_initial_data
unset -f seed_initial_data
