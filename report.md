# 图书管理系统实验报告
## 环境配置
电脑配置为ubuntu 22.04
我使用的数据库是mysql
在 /application.properties 中定义了后端端口为8000
``` txt
server.port=8000
```
在 /application.yaml 中定义了mysql的帐号、密码，以及数据库名称
``` txt
host: "localhost"
port: "3306"
user: "lhc"
password: "123456"
db: "library"
type: "mysql"
```
后端采用的是spring框架.前端使用vue
## 业务层逻辑实现
业务层的基类在/LibraryManagementSystem;我们的实现在 /LibraryManagementSystemImpl

### JDBC
后端与数据库交互中，主要使用JDBC APi.
其主要分为四个部分:
* drivermanager，负责加载和注册JDBC驱动:
``` java
Connection conn = DriverManager.getConnection(url, user, password);

```
* Connection，连接接口
``` java
conn.setAutoCommit(false);  // 设置事务
conn.commit();              // 提交事务
conn.close();               // 关闭连接
```
* Statement/PreparedStatement（语句接口）
``` java
Statement stmt = con.createStatement();
ResultSet rs = pstmt.executeQuery(); 
int rows = pstmt.executeUpdate(); 
pstmt.executeBatch();

```
* ResultSet
``` java
// 存储查询结果
ResultSet rs = pstmt.executeQuery();
while (rs.next()) {
    String name = rs.getString("name");
    int id = rs.getInt("id");
}
```
在语句中，我选择使用了preparedStatement,相较于Statement,其实现了参数自动转义，可以防止sql注入

### API实现
此处只记录一些踩雷之处，不会全盘记录

storeBook(Book book):注意此处的BookId是自增长的，不需要手动传递

incBookStock(int bookId, int deltaStock)：此处注意的是，要在业务层判断Stock>=0,否则不允许

storeBook(List<Book> books)：采用executeBatch()做批处理

removeBook(int bookId)：先检查下是否有对应的bookId,有的话再删除

borrowBook(Borrow borrow):先判断是否有书，然后更新card与borrow list

returnBook(Borrow borrow):先根据test的要求检查下是否存在borrow,然后更新card与borrow

showBorrowHistory(int cardId)：注意默认按照bookId排序，否则没法通过测试

## 接口层设计

### 1. 概述
接口层（Controller层）负责处理HTTP请求，作为系统对外提供RESTful API的入口。该层采用Spring MVC框架实现，接收前端请求参数，调用业务逻辑层（LibraryManagementSystem）完成具体操作，并返回统一的响应格式。

### 2. 核心功能模块

#### 2.1 图书管理接口

| 接口路径 | 方法 | 功能描述 |
|---------|------|----------|
| `/book` | GET | 条件查询图书，支持多字段筛选、年份/价格范围查询、排序 |
| `/book` | POST | 新增单本图书 |
| `/book/import` | POST | 批量导入JSON格式图书数据 |
| `/book/{id}` | PUT | 修改图书信息 |
| `/book/{id}` | DELETE | 删除图书 |
| `/book/{id}/stock` | POST | 增加图书库存 |

**关键技术点：**
- 使用 `@RequestParam` 接收查询参数，支持可选参数
- 自定义参数解析器将字符串转换为枚举类型（SortColumn、SortOrder）
- 参数校验与错误处理，返回400/500状态码

#### 2.2 借阅卡管理接口

| 接口路径 | 方法 | 功能描述 |
|---------|------|----------|
| `/card` | GET | 查询所有借阅卡 |
| `/card` | POST | 创建借阅卡 |
| `/card/{id}` | PUT | 注册/更新借阅卡信息 |
| `/card/{id}` | DELETE | 删除借阅卡 |

#### 2.3 借还书管理接口

| 接口路径 | 方法 | 功能描述 |
|---------|------|----------|
| `/borrow` | POST | 借书操作 |
| `/borrow/return` | POST | 还书操作 |
| `/borrow` | GET | 查询指定借阅卡的借阅历史（参数：cardID） |

### 3. 请求/响应设计

#### 3.1 请求体示例

**创建图书：**
```json
{
  "category": "计算机科学",
  "title": "深入理解Java虚拟机",
  "press": "机械工业出版社",
  "publishYear": 2019,
  "author": "周志明",
  "price": 89.00,
  "stock": 10
}
```

**借书请求：**
```json
{
  "cardId": 1001,
  "bookId": 2001
}
```

#### 3.2 响应格式
- 成功：返回业务数据或空体（204 No Content）
- 失败：返回错误消息字符串，配合适当HTTP状态码

### 4. 关键实现说明

#### 4.1 参数校验
- 批量导入时对每行数据进行非空校验和范围校验
- 枚举参数支持多种输入格式（如排序方式支持 "asc"、"ASC"、"ASCENDING"）

#### 4.2 异常处理
- 参数错误：返回 `HttpStatus.BAD_REQUEST` (400)
- 业务逻辑错误：返回 `HttpStatus.INTERNAL_SERVER_ERROR` (500)
- 文件导入错误：明确提示行号及错误原因

#### 4.3 依赖注入
通过构造函数注入 `LibraryManagementSystem` 业务层组件，遵循单一职责原则，接口层仅关注请求响应转换。

### 5. 接口层与业务层交互
接口层不包含具体业务逻辑，所有操作委托给 `libraryManagementSystem` 的对应方法，通过 `ApiResult` 对象获取执行结果状态和载荷数据，实现关注点分离。

## 前端界面层设计

### 1. 概述

前端采用 **Vue 3** 框架结合 **Element Plus** 组件库开发，通过 **Axios** 库与后端 RESTful API 进行交互。界面层主要包含三个核心模块：图书管理、借书证管理、借书记录查询。

### 2. 技术栈

| 技术 | 用途 |
|------|------|
| Vue 3 | 响应式数据绑定、组件化开发 |
| Element Plus | UI 组件库（表格、表单、对话框、消息提示） |
| Axios | HTTP 请求封装，与后端 API 通信 |
| Vue Computed | 前端数据过滤与搜索 |

### 3. 功能模块

#### 3.1 图书管理模块（Book.vue）

**主要功能：**
- 多条件组合查询图书（分类、书名、出版社、作者、年份范围、价格范围）
- 排序功能（支持多字段升序/降序）
- 新增、编辑、删除图书
- 库存调整（增加/减少）
- 批量导入（支持 JSON 文件）

**核心界面组件：**
```vue
<el-table :data="books" border>
  <el-table-column prop="bookId" label="图书ID" />
  <el-table-column prop="title" label="书名" />
  <!-- 操作列包含编辑、库存调整、删除按钮 -->
</el-table>
```

**关键交互流程：**
1. 用户填写查询条件 → 点击查询 → 发送 GET `/book` 请求 → 渲染表格
2. 点击新增 → 弹窗表单 → 发送 POST `/book` → 刷新列表
3. 点击导入 → 选择 JSON 文件 → 发送 POST `/book/import`（multipart/form-data）

#### 3.2 借书证管理模块（Card.vue）

**主要功能：**
- 查询所有借书证（卡片式展示）
- 创建借书证
- 删除借书证
- 前端实时搜索过滤

**界面特点：**
- 采用**卡片布局**（flex 弹性布局）展示借书证信息
- 支持**实时搜索**（通过 `v-show` 结合 `includes` 过滤）
- 新增/删除操作使用**对话框（Dialog）**确认

**核心代码逻辑：**
```javascript
QueryCards() {
  axios.get('/card')
    .then(response => {
      response.data.cards.forEach(card => {
        this.cards.push(normalizeCard(card))
      })
    })
}
```

#### 3.3 借书记录模块（Borrow.vue）

**主要功能：**
- 按借书证 ID 查询借阅历史
- 借书操作
- 还书操作
- 结果表格支持排序和搜索过滤

**关键实现：**
```javascript
// 借书
await axios.post('/borrow', {
  cardId: Number(this.borrowForm.cardId),
  bookId: Number(this.borrowForm.bookId)
})

// 还书
await axios.post('/borrow/return', {
  cardId: Number(this.borrowForm.cardId),
  bookId: Number(this.borrowForm.bookId)
})
```

**计算属性实现前端过滤：**
```javascript
computed: {
  fitlerTableData() {
    return this.tableData.filter(tuple =>
      this.toSearch == '' ||
      tuple.bookID == this.toSearch ||
      tuple.borrowTime.includes(this.toSearch)
    )
  }
}
```

### 4. 与后端交互设计

| 功能 | HTTP 方法 | 接口路径 | 请求体/参数 |
|------|-----------|----------|-------------|
| 查询图书 | GET | `/book` | Query 参数 |
| 新增图书 | POST | `/book` | JSON |
| 批量导入 | POST | `/book/import` | FormData |
| 编辑图书 | PUT | `/book/{id}` | JSON |
| 删除图书 | DELETE | `/book/{id}` | - |
| 库存调整 | POST | `/book/{id}/stock` | URL 参数 `delta` |
| 获取所有借书证 | GET | `/card` | - |
| 新增借书证 | POST | `/card` | JSON |
| 删除借书证 | DELETE | `/card/{id}` | - |
| 借书 | POST | `/borrow` | JSON |
| 还书 | POST | `/borrow/return` | JSON |
| 查询借阅历史 | GET | `/borrow` | Query 参数 `cardID` |

### 5. 数据规范化处理

前端对后端返回的枚举类型进行转换，确保界面显示友好：

```javascript
function normalizeCardType(type) {
  if (type === 'Teacher' || type === '教师' || type === 'T') {
    return '教师'
  }
  return '学生'
}
```

### 6. 用户体验优化

- **加载即查询**：组件 `mounted` 生命周期自动请求数据
- **操作反馈**：所有请求使用 `ElMessage` 提供成功/失败提示
- **确认机制**：删除操作使用 `ElMessageBox.confirm` 二次确认
- **表单验证**：必填项校验（如新建借书证时姓名、部门不能为空）
- **文件上传**：隐藏的 `<input type="file">` 触发导入，保持界面简洁

### 7. 组件生命周期管理

```javascript
mounted() {
  this.QueryCards()  // Card.vue 加载时查询借书证
  this.queryBooks()  // Book.vue 加载时查询图书
}
```



## 实验结果展示
验收时已经展示前后端，此处再贴一下过test的截图
![alt text](<截图 2026-04-27 11-40-21.png>)
## 思考题
### E-R图
![alt text](<截图 2026-04-27 11-57-51.png>)
### sql注入
SQl注入的原理:应用程序没有对用户输入的数据进行严格的过滤或转义，直接将输入拼接到SQL查询语句中。攻击者通过构造特殊的输入，改变了原有SQL语句的语义，从而绕过身份验证、窃取数据甚至删除整个数据库。

假设登录模块的原始SQL逻辑是：

SELECT * FROM users WHERE username = ' + userInput + ' AND password = '123456';

正常输入： admin -> 逻辑正常。

攻击输入： admin' OR '1'='1

生成的SQL： SELECT * FROM users WHERE username = 'admin' OR '1'='1' AND password = '123456';

由于 '1'='1' 永远成立，数据库会忽略密码验证，直接让攻击者登录成功。

我在代码中基本都使用了 PreparedStatement ,所以风险较小，已解决
### 并发访问
快照读机制意味着永远读到目前最新提交事件的版本，如果此时b修改了数据但没提交事务，就会出现问题!
当前读机制强制读取数据库当前最真实的状态，并阻塞其他尝试修改该记录的事务，即使用悲观锁

因此，我们在代码中可以写为SELECT stock FROM book WHERE book_id = ? FOR UPDATE;这样别的查询需要等待!

另外，我们可以在代码中增加判断，
String decStockSql = "update book set stock = stock - 1 where book_id = ? and stock > 0"; 这样就会强制保证逻辑判断