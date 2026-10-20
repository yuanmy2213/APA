# APA · 财经权限审查报告系统一期 V1.4.0

财经账号权限审查 Finance Account Permission Audit：审查财经领域产品的用户权限数据（iauth/公服），检查用户权限、权限设计是否符合要求（IT权限与业务权限隔离、SOD职责分离、特权账号管理、非雇员权限控制等）。

已实现 Vue + Java 运行版：上传四类核心审查数据，校验并形成草稿，修正报告或原始数据副本，发布后供领导查看看板、汇总、风险明细和原始记录。总体架构见 [总体架构设计](docs/总体架构设计_V1.0.md)，一期具体实现见 [一期详细设计](docs/一期详细设计_V1.0.md)。全部设计与验证材料从 [文档入口](docs/README.md) 阅读。

## 直接运行

需要 Java 17 或更高兼容环境。交付包内已包含可运行 JAR，无需安装 Node、Maven 或 Python。

- Windows：双击 `start-demo.bat`。
- Linux/macOS：运行 `bash start-demo.sh`。
- 浏览器打开 http://127.0.0.1:8080 。

体验账号：`aa / aa-demo-2026`。只读领导账号：`leader / leader-demo-2026`。产品账号：`product / product-demo-2026`（仅P06财经法务产品，可修订不可发布）。质量运营：`quality / quality-demo-2026`。

首次运行没有业务数据；`data/`保存数据库和原始文件，重启后保留。下载工作台模板或上传包内 `examples/财经法务_四类核心数据_演示.xlsx`，选择“财经法务产品”，原始数据覆盖选择“完整”，校验后预览发布。示例为虚构数据，不代表真实权限情况。

## 完整体验顺序

1. 登录 → 数据管理 → 导入审查数据；选择产品、来源时间和覆盖状态。
2. 文件在服务端校验，失败可下载错误；成功形成待发布草稿。
3. 核对两条链路的数量、时点、覆盖和未关联，填写原因发布。
4. 首页 → 产品汇总 → 风险明细 → 原始数据；也可从数据管理独立进入原始记录。
5. 修改原始副本或修正报告，保存到草稿后预览发布；原始修改关联的风险进入待复核。
6. 在数据管理查看发布历史，可引用旧数据发布新版本实现回退。

## 源码与构建

- `frontend/`：正式运行版 Vue 界面，由 Java 同源托管。
- `server/`：Java 后端、数据库迁移与集成测试。
- `prototype/`：[高保真原型源码及启动方法](prototype/README.md)，[在线预览](https://finance-access-review-20261016.garden-rook-7646.chatgpt.site/)，不连接 Java 数据库。
- `docs/`：[总体架构、一期详细设计、原型交互及验证记录](docs/README.md)。
- `pom.xml`：执行 `mvn test`，然后 `mvn package`；产物为 `target/finance-access-review-1.4.0.jar`。

可重复验证：`mvn test`；`python3 scripts/verify-release.py`（需先打包）；`cd qa && npm install && npm test`（仅DOM测试需要Node）。

运行版无需前端构建步骤，Vue依赖已随包提供。JAR打包时将 `frontend/` 复制到静态资源目录。当前在线演示的文案已按标注同步修改。

## 正式部署配置

使用 `--spring.profiles.active=prod`，并设置 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`FILE_STORAGE`、`AA_PASSWORD` 等。未配置正式账号时启动失败，不会自动使用体验密码。`PRODUCT_SCOPE`控制产品账号范围，默认P06；正式环境应接入企业统一认证和可信的IAM授权来源。

`compose.yaml`为PostgreSQL与单Java实例部署示例。数据库和文件存储一起备份。生产部署、SSO和企业数据库接入尚需在目标环境验证；本轮交付为可运行、可继续集成的一期工程。

## 数据边界

上传和编辑影响本系统数据，不操作生产iAuth。只上传风险不会推断授权/用户总量，数据不足会显示未提供或部分覆盖。修改原始副本不会自动消除风险；确认误报需要原因并保留导入记录与历史版本。完整空清单需要额外确认。


## V1.4.0 当前导入契约

- 四类模板及JSON每行必填产品名称，与上传所选产品一致；Excel提供产品下拉选择。导出明细同样带产品名称。
- 原始权限点、矩阵风险必填权限组合ID；用户角色风险必填授权记录ID。按业务ID在同一产品和版本内关联，去掉自动生成旧ID和名称兜底；不改风险原因。
- 原始授权没有授权记录ID时仍逐行完整保留，不强制去重；这类原始行不参与风险ID关联。有ID时按来源、应用、租户及ID判重。
- 单份V1脚本初始化数据库，删除旧开发库迁移回填。尚未上线的旧测试数据无需迁移；首次使用本版须空库，重建后同版本重启正常保留数据。
- 保留已审核UI、按检查项的风险环形图、误报剔除、草稿发布、版本差异、全量错误报告，以及上版的批量处理、独立编辑组件和查询防抖。

若使用旧版体验库，请先停止应用，把部署目录的data文件夹改名为data-pre-v1.4（或直接使用新的部署目录），再启动并下载本版模板重新导入。自定义DB_URL时需指向新的空库，FILE_STORAGE指向新的目录。不要用Flyway repair绕过旧库校验，也不会在启动时自动删除数据。操作示例见[发布说明](docs/发布说明_V1.4.0.md)。

## 部署包与数据接口

构建后执行`python3 scripts/package-release.py`，生成target/package/finance-access-review。将其放入独立目录，配置数据库、账号和文件存储，运行bin/start.sh。该启动模板受版本控制，默认CONTEXT_PATH=/apa；设为/使用根路径。默认生产profile，体验可设SPRING_PROFILES_ACTIVE=demo。前端相对api路径与全部静态资源跟随前缀。

JSON/文件上传和修改接口见[调用说明](docs/数据接口调用说明_V1.md)、[OpenAPI](docs/openapi-integration-v1.yaml)，客户端为[scripts/integration-client.py](scripts/integration-client.py)。启用需设置INTEGRATION_TOKEN（至少32字符）及INTEGRATION_PRODUCTS。上传/修订先形成草稿，默认由AA/质量运营发布；可明确配置INTEGRATION_CAN_PUBLISH=true后使用发布接口。页面认证与CSRF独立保留。

设计、用例和验证统一从[文档入口](docs/README.md)阅读。
