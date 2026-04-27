<template>
    <el-scrollbar height="100%" style="width: 100%; height: 100%;">
        <div style="margin-top: 20px; margin-left: 40px; margin-right: 40px;">


            <div style="font-size: 2em; font-weight: bold;">图书管理</div>



            <div style="margin-top: 24px; display: flex; gap: 12px; flex-wrap: wrap; align-items: center;">
                <el-input v-model="query.category" placeholder="分类" clearable style="width: 180px;" />
                <el-input v-model="query.title" placeholder="书名" clearable style="width: 220px;" />
                <el-input v-model="query.press" placeholder="出版社" clearable style="width: 220px;" />
                <el-input v-model="query.author" placeholder="作者" clearable style="width: 180px;" />
                <el-input-number v-model="query.minPublishYear" :min="1900" :max="2100" placeholder="最小年份" style="width: 160px;" />
                <el-input-number v-model="query.maxPublishYear" :min="1900" :max="2100" placeholder="最大年份" style="width: 160px;" />
                <el-input-number v-model="query.minPrice" :min="0" :precision="2" :step="1" placeholder="最低价格" style="width: 160px;" />
                <el-input-number v-model="query.maxPrice" :min="0" :precision="2" :step="1" placeholder="最高价格" style="width: 160px;" />
                <el-select v-model="query.sortBy" placeholder="排序字段" style="width: 150px;">
                    <el-option label="图书ID" value="BOOK_ID" />
                    <el-option label="分类" value="CATEGORY" />
                    <el-option label="书名" value="TITLE" />
                    <el-option label="出版社" value="PRESS" />
                    <el-option label="出版年份" value="PUBLISH_YEAR" />
                    <el-option label="作者" value="AUTHOR" />
                    <el-option label="价格" value="PRICE" />
                    <el-option label="库存" value="STOCK" />
                </el-select>
                <el-select v-model="query.sortOrder" placeholder="排序方式" style="width: 120px;">
                    <el-option label="升序" value="ASC" />
                    <el-option label="降序" value="DESC" />
                </el-select>
                <el-button type="primary" @click="queryBooks">查询</el-button>
                <el-button @click="resetQuery">重置</el-button>
                <el-button type="success" @click="openCreateDialog">新增图书</el-button>
                <el-button type="warning" @click="openImportDialog">批量导入</el-button>
            </div>

            <input
                ref="bookImportInput"
                type="file"
                accept=".csv,.json"
                style="display: none"
                @change="handleImportFileChange"
            />



            <el-table :data="books" style="width: 100%; margin-top: 20px;" border>
                <el-table-column prop="bookId" label="图书ID" width="90" />
                <el-table-column prop="category" label="分类" width="140" />
                <el-table-column prop="title" label="书名" min-width="220" />
                <el-table-column prop="press" label="出版社" min-width="180" />
                <el-table-column prop="publishYear" label="年份" width="100" />
                <el-table-column prop="author" label="作者" width="140" />
                <el-table-column prop="price" label="价格" width="100">
                    <template #default="scope">{{ Number(scope.row.price).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="stock" label="库存" width="90" />
                <el-table-column label="操作" width="280" fixed="right">
                    <template #default="scope">
                        <el-button size="small" @click="openEditDialog(scope.row)">编辑</el-button>
                        <el-button size="small" type="warning" @click="openStockDialog(scope.row)">库存调整</el-button>
                        <el-button size="small" type="danger" @click="removeBook(scope.row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>



        </div>


        <el-dialog v-model="createDialogVisible" title="新增图书" width="480px">
            <el-form label-width="80px">
                <el-form-item label="分类"><el-input v-model="createForm.category" /></el-form-item>
                <el-form-item label="书名"><el-input v-model="createForm.title" /></el-form-item>
                <el-form-item label="出版社"><el-input v-model="createForm.press" /></el-form-item>
                <el-form-item label="年份"><el-input-number v-model="createForm.publishYear" :min="1900" :max="2100" /></el-form-item>
                <el-form-item label="作者"><el-input v-model="createForm.author" /></el-form-item>
                <el-form-item label="价格"><el-input-number v-model="createForm.price" :min="0" :precision="2" :step="1" /></el-form-item>
                <el-form-item label="库存"><el-input-number v-model="createForm.stock" :min="0" :step="1" /></el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="createDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="createBook">确定</el-button>
            </template>
        </el-dialog>

        <el-dialog v-model="editDialogVisible" title="编辑图书" width="480px">
            <el-form label-width="80px">
                <el-form-item label="分类"><el-input v-model="editForm.category" /></el-form-item>
                <el-form-item label="书名"><el-input v-model="editForm.title" /></el-form-item>
                <el-form-item label="出版社"><el-input v-model="editForm.press" /></el-form-item>
                <el-form-item label="年份"><el-input-number v-model="editForm.publishYear" :min="1900" :max="2100" /></el-form-item>
                <el-form-item label="作者"><el-input v-model="editForm.author" /></el-form-item>
                <el-form-item label="价格"><el-input-number v-model="editForm.price" :min="0" :precision="2" :step="1" /></el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="editDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="updateBook">确定</el-button>
            </template>
        </el-dialog>

        <el-dialog v-model="stockDialogVisible" title="库存调整" width="420px">
            <div>图书ID: {{ currentBookId }}</div>
            <div style="margin-top: 12px;">变化量（可为负）:</div>
            <el-input-number v-model="stockDelta" :step="1" style="margin-top: 8px;" />
            <template #footer>
                <el-button @click="stockDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="changeStock">确定</el-button>
            </template>
        </el-dialog>









    </el-scrollbar>
</template>

<script>
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

function defaultBookForm() {
    return {
        category: '',
        title: '',
        press: '',
        publishYear: 2024,
        author: '',
        price: 0,
        stock: 1
    }
}

function defaultQuery() {
    return {
        category: '',
        title: '',
        press: '',
        author: '',
        minPublishYear: null,
        maxPublishYear: null,
        minPrice: null,
        maxPrice: null,
        sortBy: 'BOOK_ID',
        sortOrder: 'ASC'
    }
}

export default {
    // 响应数据，自动刷新
    data() {
        return {
            books: [],
            query: defaultQuery(),
            createDialogVisible: false,
            editDialogVisible: false,
            stockDialogVisible: false,
            createForm: defaultBookForm(),
            editForm: defaultBookForm(),
            currentBookId: 0,
            stockDelta: 0
        }
    },
    methods: {
        buildQueryParams() {
            const params = {}
            if (this.query.category?.trim()) params.category = this.query.category.trim()
            if (this.query.title?.trim()) params.title = this.query.title.trim()
            if (this.query.press?.trim()) params.press = this.query.press.trim()
            if (this.query.author?.trim()) params.author = this.query.author.trim()
            if (this.query.minPublishYear != null) params.minPublishYear = this.query.minPublishYear
            if (this.query.maxPublishYear != null) params.maxPublishYear = this.query.maxPublishYear
            if (this.query.minPrice != null) params.minPrice = this.query.minPrice
            if (this.query.maxPrice != null) params.maxPrice = this.query.maxPrice
            if (this.query.sortBy) params.sortBy = this.query.sortBy
            if (this.query.sortOrder) params.sortOrder = this.query.sortOrder
            return params
        },
        async queryBooks() {
            try {
                const { data } = await axios.get('/book', { params: this.buildQueryParams() })
                this.books = data.results 
            } catch (error) {
                ElMessage.error(error?.response?.data?.message || '查询图书失败')
            }
        },
        resetQuery() {
            this.query = defaultQuery()
            this.queryBooks()
        },
        openCreateDialog() {
            this.createForm = defaultBookForm()
            this.createDialogVisible = true
        },
        openImportDialog() {
            this.$refs.bookImportInput.click()
        },
        async handleImportFileChange(event) {
            const file = event?.target?.files?.[0]
            if (!file) {
                return
            }
            await this.importBooks(file)
            event.target.value = ''
        },
        async importBooks(file) {
            try {
                const formData = new FormData()
                formData.append('file', file)
                const { data } = await axios.post('/book/import', formData, {
                    headers: {
                        'Content-Type': 'multipart/form-data'
                    }
                })
                const count = data?.count
                if (typeof count === 'number') {
                    ElMessage.success(`批量导入成功，共导入 ${count} 本图书`)
                } else {
                    ElMessage.success('批量导入成功')
                }
                this.queryBooks()
            } catch (error) {
                const backendMessage = typeof error?.response?.data === 'string'
                    ? error.response.data
                    : error?.response?.data?.message
                ElMessage.error(backendMessage || '批量导入失败')
            }
        },
        async createBook() {
            try {
                await axios.post('/book', this.createForm)
                ElMessage.success('新增图书成功')
                this.createDialogVisible = false
                this.queryBooks()
            } catch (error) {
                ElMessage.error(error?.response?.data?.message || '新增图书失败')
            }
        },
        openEditDialog(book) {
            this.currentBookId = book.bookId
            this.editForm = {
                category: book.category,
                title: book.title,
                press: book.press,
                publishYear: book.publishYear,
                author: book.author,
                price: Number(book.price),
                stock: Number(book.stock)
            }
            this.editDialogVisible = true
        },
        async updateBook() {
            try {
                await axios.put(`/book/${this.currentBookId}`, this.editForm)
                ElMessage.success('修改图书成功')
                this.editDialogVisible = false
                this.queryBooks()
            } catch (error) {
                ElMessage.error(error?.response?.data?.message || '修改图书失败')
            }
        },
        openStockDialog(book) {
            this.currentBookId = book.bookId
            this.stockDelta = 0
            this.stockDialogVisible = true
        },
        async changeStock() {
            try {
                await axios.post(`/book/${this.currentBookId}/stock`, null, {
                    params: { delta: this.stockDelta }
                })
                ElMessage.success('库存调整成功')
                this.stockDialogVisible = false
                this.queryBooks()
            } catch (error) {
                ElMessage.error(error?.response?.data?.message || '库存调整失败')
            }
        },
        async removeBook(book) {
            try {
                await ElMessageBox.confirm(`确认删除图书《${book.title}》吗？`, '删除确认', {
                    type: 'warning'
                })
                await axios.delete(`/book/${book.bookId}`)
                ElMessage.success('删除图书成功')
                this.queryBooks()
            } catch (error) {
                if (error !== 'cancel') {
                    ElMessage.error(error?.response?.data?.message || '删除图书失败')
                }
            }
        }
    },
    mounted() {
        this.queryBooks()// 页面加载时查询图书
    }
}
</script>