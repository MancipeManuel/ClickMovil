package co.edu.ucompensar.clickmovil.data

import android.content.ContentValues
import android.content.Context
import co.edu.ucompensar.clickmovil.model.SalesTotals
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import co.edu.ucompensar.clickmovil.model.CartLine
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.model.OrderItem
import co.edu.ucompensar.clickmovil.model.Product
import co.edu.ucompensar.clickmovil.model.Role
import co.edu.ucompensar.clickmovil.model.StoreOrder
import co.edu.ucompensar.clickmovil.model.User
import co.edu.ucompensar.clickmovil.util.Password
import co.edu.ucompensar.clickmovil.util.shippingFor
import kotlin.random.Random

class Repository(context: Context) {
    private val recovery = context.applicationContext.getSharedPreferences("clickmovil_recovery", Context.MODE_PRIVATE)
    private val db: SQLiteDatabase = DbHelper.get(context).writableDatabase

    fun login(email: String, password: String): User? {
        val user = userByEmail(email.trim().lowercase()) ?: return null
        if (!user.active) return null
        val hash = passwordHash(user.id) ?: return null
        if (hash != Password.hash(password)) return null
        return user
    }

    fun register(
        name: String,
        email: String,
        password: String,
        phone: String,
        city: String,
        role: Role
    ): OpResult<User> {
        if (role == Role.ADMIN) {
            return OpResult.Err("No puedes asignarte el rol de administrador.")
        }
        val cleanEmail = email.trim().lowercase()
        if (userByEmail(cleanEmail) != null) {
            return OpResult.Err("Ese correo ya está registrado.")
        }
        val values = ContentValues().apply {
            put("name", name.trim())
            put("email", cleanEmail)
            put("password_hash", Password.hash(password))
            put("phone", phone.trim())
            put("city", city.trim())
            put("role", role.name)
            put("active", 1)
        }
        val id = db.insert("users", null, values)
        val user = userById(id) ?: return OpResult.Err("No se pudo crear la cuenta.")
        return OpResult.Ok(user)
    }

    fun userById(id: Long): User? {
        db.rawQuery("SELECT * FROM users WHERE id = ?", arrayOf(id.toString())).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return cursor.toUser()
        }
    }

    fun users(): List<User> {
        val list = mutableListOf<User>()
        db.rawQuery("SELECT * FROM users ORDER BY name", null).use { cursor ->
            while (cursor.moveToNext()) list.add(cursor.toUser())
        }
        return list
    }

    fun updateProfile(userId: Long, name: String, phone: String, city: String): OpResult<User> {
        if (name.isBlank()) return OpResult.Err("El nombre es obligatorio.")
        val values = ContentValues().apply {
            put("name", name.trim())
            put("phone", phone.trim())
            put("city", city.trim())
        }
        db.update("users", values, "id = ?", arrayOf(userId.toString()))
        val user = userById(userId) ?: return OpResult.Err("No se encontró la cuenta.")
        return OpResult.Ok(user)
    }

    fun setActive(actorId: Long, userId: Long, active: Boolean): OpResult<Unit> {
        if (actorId == userId) return OpResult.Err("No puedes desactivar tu propia cuenta.")
        val target = userById(userId) ?: return OpResult.Err("Usuario no encontrado.")
        if (target.role == Role.ADMIN && !active && activeAdmins() <= 1) {
            return OpResult.Err("Debe quedar al menos un administrador activo.")
        }
        val values = ContentValues().apply { put("active", if (active) 1 else 0) }
        db.update("users", values, "id = ?", arrayOf(userId.toString()))
        return OpResult.Ok(Unit)
    }

    fun setRole(actorId: Long, userId: Long, role: Role): OpResult<Unit> {
        val target = userById(userId) ?: return OpResult.Err("Usuario no encontrado.")
        if (actorId == userId && role != Role.ADMIN) {
            return OpResult.Err("No puedes quitarte el rol de administrador.")
        }
        if (target.role == Role.ADMIN && role != Role.ADMIN && activeAdmins() <= 1) {
            return OpResult.Err("Debe quedar al menos un administrador.")
        }
        val values = ContentValues().apply { put("role", role.name) }
        db.update("users", values, "id = ?", arrayOf(userId.toString()))
        return OpResult.Ok(Unit)
    }

    fun startRecovery(email: String): OpResult<String> {
        val user = userByEmail(email.trim().lowercase())
            ?: return OpResult.Err("No encontramos una cuenta con ese correo.")
        if (!user.active) return OpResult.Err("Esa cuenta está desactivada. Escribe al administrador.")
        val code = (100000 + Random.nextInt(900000)).toString()
        recovery.edit().putString(KEY_EMAIL, user.email).putString(KEY_CODE, code).apply()
        return OpResult.Ok(code)
    }

    fun resetPassword(email: String, code: String, newPassword: String): OpResult<Unit> {
        if (newPassword.length < 6) return OpResult.Err("La contraseña debe tener al menos 6 caracteres.")
        val expectedEmail = recovery.getString(KEY_EMAIL, null)
        val expectedCode = recovery.getString(KEY_CODE, null)
        if (expectedEmail == null || !expectedEmail.equals(email.trim(), ignoreCase = true) || expectedCode != code.trim()) {
            return OpResult.Err("El código no coincide con el correo.")
        }
        val user = userByEmail(expectedEmail) ?: return OpResult.Err("No encontramos la cuenta.")
        val values = ContentValues().apply { put("password_hash", Password.hash(newPassword)) }
        db.update("users", values, "id = ?", arrayOf(user.id.toString()))
        recovery.edit().clear().apply()
        return OpResult.Ok(Unit)
    }

    fun saveManagedUser(
        actorId: Long,
        userId: Long?,
        name: String,
        email: String,
        password: String,
        phone: String,
        city: String,
        role: Role
    ): OpResult<User> {
        if (userById(actorId)?.role != Role.ADMIN) return OpResult.Err("Solo un administrador puede gestionar cuentas.")
        if (name.isBlank() || email.isBlank()) return OpResult.Err("Nombre y correo son obligatorios.")
        val cleanEmail = email.trim().lowercase()
        val existingEmail = userByEmail(cleanEmail)
        if (existingEmail != null && existingEmail.id != userId) {
            return OpResult.Err("Ese correo ya está registrado.")
        }
        if (userId == null) {
            if (password.length < 6) return OpResult.Err("La contraseña debe tener al menos 6 caracteres.")
            val values = ContentValues().apply {
                put("name", name.trim())
                put("email", cleanEmail)
                put("password_hash", Password.hash(password))
                put("phone", phone.trim())
                put("city", city.trim())
                put("role", role.name)
                put("active", 1)
            }
            val id = db.insert("users", null, values)
            return userById(id)?.let { OpResult.Ok(it) } ?: OpResult.Err("No se pudo crear el usuario.")
        }
        val target = userById(userId) ?: return OpResult.Err("Usuario no encontrado.")
        if (target.role == Role.ADMIN && role != Role.ADMIN && activeAdmins() <= 1) {
            return OpResult.Err("Debe quedar al menos un administrador.")
        }
        val values = ContentValues().apply {
            put("name", name.trim())
            put("email", cleanEmail)
            put("phone", phone.trim())
            put("city", city.trim())
            put("role", role.name)
            if (password.isNotBlank()) put("password_hash", Password.hash(password))
        }
        db.update("users", values, "id = ?", arrayOf(userId.toString()))
        return userById(userId)?.let { OpResult.Ok(it) } ?: OpResult.Err("No se pudo actualizar el usuario.")
    }

    fun deleteUser(actorId: Long, userId: Long): OpResult<Unit> {
        if (actorId == userId) return OpResult.Err("No puedes eliminar tu propia cuenta.")
        val target = userById(userId) ?: return OpResult.Err("Usuario no encontrado.")
        if (target.role == Role.ADMIN && activeAdmins() <= 1) {
            return OpResult.Err("Debe quedar al menos un administrador.")
        }
        if (countOf("orders", "buyer_id = ?", userId) > 0 ||
            countOf("order_items", "seller_id = ?", userId) > 0 ||
            countOf("products", "seller_id = ?", userId) > 0
        ) {
            return OpResult.Err("Esta cuenta tiene productos o pedidos. Desactívala para conservar la trazabilidad.")
        }
        db.delete("cart_items", "user_id = ?", arrayOf(userId.toString()))
        db.delete("users", "id = ?", arrayOf(userId.toString()))
        return OpResult.Ok(Unit)
    }

    fun salesTotals(): SalesTotals {
        val orders = allOrders()
        return SalesTotals(
            count = orders.size,
            revenue = orders.filter { it.status != "CANCELADO" }.sumOf { it.total },
            paid = orders.count { it.status == "PAGADO" },
            shipped = orders.count { it.status == "ENVIADO" },
            delivered = orders.count { it.status == "ENTREGADO" },
            cancelled = orders.count { it.status == "CANCELADO" }
        )
    }

    private fun countOf(table: String, where: String, id: Long): Int {
        db.rawQuery("SELECT COUNT(*) FROM $table WHERE $where", arrayOf(id.toString())).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    fun products(category: String?, query: String?, sellerId: Long?, includeInactive: Boolean): List<Product> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (!includeInactive) where.add("active = 1")
        if (!category.isNullOrBlank()) {
            where.add("category = ?")
            args.add(category)
        }
        if (!query.isNullOrBlank()) {
            where.add("(name LIKE ? OR compatibility LIKE ?)")
            args.add("%${query.trim()}%")
            args.add("%${query.trim()}%")
        }
        if (sellerId != null) {
            where.add("seller_id = ?")
            args.add(sellerId.toString())
        }
        val sql = buildString {
            append("SELECT * FROM products")
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY name")
        }
        val list = mutableListOf<Product>()
        db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) list.add(cursor.toProduct())
        }
        return list
    }

    fun product(id: Long): Product? {
        db.rawQuery("SELECT * FROM products WHERE id = ?", arrayOf(id.toString())).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return cursor.toProduct()
        }
    }

    fun saveProduct(
        actor: User,
        productId: Long?,
        name: String,
        description: String,
        price: Long,
        category: String,
        compatibility: String,
        stock: Int,
        imagePath: String?
    ): OpResult<Long> {
        if (name.isBlank()) return OpResult.Err("El nombre es obligatorio.")
        if (price <= 0) return OpResult.Err("El precio debe ser mayor que cero.")
        if (stock < 0) return OpResult.Err("Las existencias no pueden ser negativas.")
        if (description.isBlank()) return OpResult.Err("Agrega una descripción del producto.")
        val existing = productId?.let { product(it) }
        if (existing != null && actor.role == Role.VENDEDOR && existing.sellerId != actor.id) {
            return OpResult.Err("No puedes modificar productos de otro vendedor.")
        }
        if (actor.role == Role.COMPRADOR) {
            return OpResult.Err("Un comprador no puede publicar productos.")
        }
        val sellerId = existing?.sellerId ?: actor.id
        val values = ContentValues().apply {
            put("seller_id", sellerId)
            put("name", name.trim())
            put("description", description.trim())
            put("price", price)
            put("category", category)
            put("compatibility", compatibility.trim().ifBlank { "Universal" })
            put("stock", stock)
            put("image_path", imagePath)
            put("active", 1)
        }
        val id = if (existing == null) {
            db.insert("products", null, values)
        } else {
            db.update("products", values, "id = ?", arrayOf(existing.id.toString()))
            existing.id
        }
        return OpResult.Ok(id)
    }

    fun retireProduct(actor: User, productId: Long): OpResult<Unit> {
        val existing = product(productId) ?: return OpResult.Err("Producto no encontrado.")
        if (actor.role == Role.VENDEDOR && existing.sellerId != actor.id) {
            return OpResult.Err("No puedes retirar productos de otro vendedor.")
        }
        if (actor.role == Role.COMPRADOR) return OpResult.Err("No tienes permiso para esta acción.")
        val values = ContentValues().apply { put("active", 0) }
        db.update("products", values, "id = ?", arrayOf(productId.toString()))
        return OpResult.Ok(Unit)
    }

    fun cart(userId: Long): List<CartLine> {
        val sql = """
            SELECT c.id AS cart_id, c.quantity AS cart_qty,
                   p.id, p.seller_id, p.name, p.description, p.price, p.category,
                   p.compatibility, p.stock, p.image_path, p.active
            FROM cart_items c
            JOIN products p ON p.id = c.product_id
            WHERE c.user_id = ?
            ORDER BY p.name
        """.trimIndent()
        val list = mutableListOf<CartLine>()
        db.rawQuery(sql, arrayOf(userId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    CartLine(
                        itemId = cursor.getLong(cursor.getColumnIndexOrThrow("cart_id")),
                        product = cursor.toProduct(),
                        quantity = cursor.getInt(cursor.getColumnIndexOrThrow("cart_qty"))
                    )
                )
            }
        }
        return list
    }

    fun addToCart(userId: Long, productId: Long, quantity: Int): OpResult<Unit> {
        val product = product(productId) ?: return OpResult.Err("El producto no existe.")
        if (!product.active) return OpResult.Err("Este producto ya no está publicado.")
        if (product.stock <= 0) return OpResult.Err("El producto está agotado.")
        val current = cartQuantity(userId, productId)
        val next = current + quantity
        if (next > product.stock) {
            return OpResult.Err("Solo hay ${product.stock} unidades disponibles.")
        }
        if (current == 0) {
            val values = ContentValues().apply {
                put("user_id", userId)
                put("product_id", productId)
                put("quantity", quantity)
            }
            db.insert("cart_items", null, values)
        } else {
            val values = ContentValues().apply { put("quantity", next) }
            db.update("cart_items", values, "user_id = ? AND product_id = ?", arrayOf(userId.toString(), productId.toString()))
        }
        return OpResult.Ok(Unit)
    }

    fun updateCartQuantity(userId: Long, productId: Long, quantity: Int): OpResult<Unit> {
        if (quantity <= 0) {
            db.delete("cart_items", "user_id = ? AND product_id = ?", arrayOf(userId.toString(), productId.toString()))
            return OpResult.Ok(Unit)
        }
        val product = product(productId) ?: return OpResult.Err("El producto no existe.")
        if (quantity > product.stock) return OpResult.Err("Solo hay ${product.stock} unidades disponibles.")
        val values = ContentValues().apply { put("quantity", quantity) }
        db.update("cart_items", values, "user_id = ? AND product_id = ?", arrayOf(userId.toString(), productId.toString()))
        return OpResult.Ok(Unit)
    }

    fun checkout(buyerId: Long, city: String): OpResult<StoreOrder> {
        val cityClean = city.trim()
        if (cityClean.isEmpty()) return OpResult.Err("Escribe la ciudad de entrega.")
        db.beginTransaction()
        try {
            val lines = cart(buyerId)
            if (lines.isEmpty()) return OpResult.Err("El carrito está vacío.")
            var subtotal = 0L
            for (line in lines) {
                val fresh = product(line.product.id) ?: return OpResult.Err("Un producto ya no está disponible.")
                if (!fresh.active || fresh.stock < line.quantity) {
                    return OpResult.Err("No hay existencias suficientes de ${fresh.name}.")
                }
                subtotal += fresh.price * line.quantity
            }
            val shipping = shippingFor(cityClean)
            val values = ContentValues().apply {
                put("code", "TMP")
                put("buyer_id", buyerId)
                put("total", subtotal + shipping)
                put("shipping", shipping)
                put("status", "PAGADO")
                put("payment_ref", "PAY-${10000 + Random.nextInt(90000)}")
                put("city", cityClean)
                put("created_at", System.currentTimeMillis())
            }
            val orderId = db.insert("orders", null, values)
            val code = "CM-${1000 + orderId}"
            db.update("orders", ContentValues().apply { put("code", code) }, "id = ?", arrayOf(orderId.toString()))
            for (line in lines) {
                val fresh = product(line.product.id)!!
                val item = ContentValues().apply {
                    put("order_id", orderId)
                    put("product_id", fresh.id)
                    put("seller_id", fresh.sellerId)
                    put("product_name", fresh.name)
                    put("quantity", line.quantity)
                    put("unit_price", fresh.price)
                }
                db.insert("order_items", null, item)
                db.execSQL(
                    "UPDATE products SET stock = stock - ? WHERE id = ?",
                    arrayOf(line.quantity, fresh.id)
                )
            }
            db.delete("cart_items", "user_id = ?", arrayOf(buyerId.toString()))
            db.setTransactionSuccessful()
            val order = orderById(orderId) ?: return OpResult.Err("No se pudo leer el pedido.")
            return OpResult.Ok(order)
        } finally {
            db.endTransaction()
        }
    }

    fun ordersForBuyer(buyerId: Long): List<StoreOrder> = ordersWhere("o.buyer_id = ?", arrayOf(buyerId.toString()))

    fun ordersForSeller(sellerId: Long): List<StoreOrder> {
        return ordersWhere(
            "o.id IN (SELECT order_id FROM order_items WHERE seller_id = ?)",
            arrayOf(sellerId.toString())
        )
    }

    fun allOrders(): List<StoreOrder> = ordersWhere("1 = 1", emptyArray())

    fun cancelOrder(buyerId: Long, orderId: Long): OpResult<Unit> {
        db.beginTransaction()
        try {
            val order = orderById(orderId) ?: return OpResult.Err("Pedido no encontrado.")
            if (order.buyerId != buyerId) return OpResult.Err("Solo puedes cancelar tus pedidos.")
            if (order.status != "PAGADO") {
                return OpResult.Err("Solo se cancela un pedido pagado que todavía no fue enviado. El registro se conserva.")
            }
            for (item in order.items) {
                db.execSQL(
                    "UPDATE products SET stock = stock + ? WHERE id = ?",
                    arrayOf(item.quantity, item.productId)
                )
            }
            db.update("orders", ContentValues().apply { put("status", "CANCELADO") }, "id = ?", arrayOf(orderId.toString()))
            db.setTransactionSuccessful()
            return OpResult.Ok(Unit)
        } finally {
            db.endTransaction()
        }
    }

    fun markShipped(sellerId: Long, orderId: Long): OpResult<Unit> {
        val order = orderById(orderId) ?: return OpResult.Err("Pedido no encontrado.")
        if (order.items.none { it.sellerId == sellerId }) {
            return OpResult.Err("Este pedido no incluye tus productos.")
        }
        if (order.status != "PAGADO") return OpResult.Err("El pedido no está pendiente de envío.")
        db.update("orders", ContentValues().apply { put("status", "ENVIADO") }, "id = ?", arrayOf(orderId.toString()))
        return OpResult.Ok(Unit)
    }

    fun markDelivered(orderId: Long): OpResult<Unit> {
        val order = orderById(orderId) ?: return OpResult.Err("Pedido no encontrado.")
        if (order.status == "CANCELADO") return OpResult.Err("Un pedido cancelado no se marca como entregado.")
        db.update("orders", ContentValues().apply { put("status", "ENTREGADO") }, "id = ?", arrayOf(orderId.toString()))
        return OpResult.Ok(Unit)
    }

    fun lowStockCount(sellerId: Long): Int {
        db.rawQuery(
            "SELECT COUNT(*) FROM products WHERE seller_id = ? AND active = 1 AND stock <= 5",
            arrayOf(sellerId.toString())
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun ordersWhere(where: String, args: Array<String>): List<StoreOrder> {
        val sql = """
            SELECT o.*, u.name
            FROM orders o
            JOIN users u ON u.id = o.buyer_id
            WHERE $where
            ORDER BY o.created_at DESC
        """.trimIndent()
        val list = mutableListOf<StoreOrder>()
        db.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                list.add(cursor.toOrder(itemsOf(id)))
            }
        }
        return list
    }

    private fun orderById(id: Long): StoreOrder? {
        val sql = """
            SELECT o.*, u.name
            FROM orders o
            JOIN users u ON u.id = o.buyer_id
            WHERE o.id = ?
        """.trimIndent()
        db.rawQuery(sql, arrayOf(id.toString())).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return cursor.toOrder(itemsOf(id))
        }
    }

    private fun itemsOf(orderId: Long): List<OrderItem> {
        val list = mutableListOf<OrderItem>()
        db.rawQuery("SELECT * FROM order_items WHERE order_id = ?", arrayOf(orderId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    OrderItem(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        orderId = cursor.getLong(cursor.getColumnIndexOrThrow("order_id")),
                        productId = cursor.getLong(cursor.getColumnIndexOrThrow("product_id")),
                        sellerId = cursor.getLong(cursor.getColumnIndexOrThrow("seller_id")),
                        productName = cursor.getString(cursor.getColumnIndexOrThrow("product_name")),
                        quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                        unitPrice = cursor.getLong(cursor.getColumnIndexOrThrow("unit_price"))
                    )
                )
            }
        }
        return list
    }

    private fun cartQuantity(userId: Long, productId: Long): Int {
        db.rawQuery(
            "SELECT quantity FROM cart_items WHERE user_id = ? AND product_id = ?",
            arrayOf(userId.toString(), productId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return 0
            return cursor.getInt(0)
        }
    }

    private fun userByEmail(email: String): User? {
        db.rawQuery("SELECT * FROM users WHERE email = ?", arrayOf(email)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return cursor.toUser()
        }
    }

    private fun passwordHash(userId: Long): String? {
        db.rawQuery("SELECT password_hash FROM users WHERE id = ?", arrayOf(userId.toString())).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return cursor.getString(0)
        }
    }

    private fun activeAdmins(): Int {
        db.rawQuery(
            "SELECT COUNT(*) FROM users WHERE role = ? AND active = 1",
            arrayOf(Role.ADMIN.name)
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun Cursor.toUser(): User {
        return User(
            id = getLong(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            email = getString(getColumnIndexOrThrow("email")),
            phone = getString(getColumnIndexOrThrow("phone")),
            city = getString(getColumnIndexOrThrow("city")),
            role = Role.from(getString(getColumnIndexOrThrow("role"))),
            active = getInt(getColumnIndexOrThrow("active")) == 1
        )
    }

    private fun Cursor.toProduct(offset: Int = 0): Product {
        fun idx(name: String) = getColumnIndexOrThrow(name).let { if (offset == 0) it else it }
        return Product(
            id = getLong(idx("id")),
            sellerId = getLong(idx("seller_id")),
            name = getString(idx("name")),
            description = getString(idx("description")),
            price = getLong(idx("price")),
            category = getString(idx("category")),
            compatibility = getString(idx("compatibility")),
            stock = getInt(idx("stock")),
            imagePath = getString(idx("image_path")),
            active = getInt(idx("active")) == 1
        )
    }

    private fun Cursor.toOrder(items: List<OrderItem>): StoreOrder {
        return StoreOrder(
            id = getLong(getColumnIndexOrThrow("id")),
            code = getString(getColumnIndexOrThrow("code")),
            buyerId = getLong(getColumnIndexOrThrow("buyer_id")),
            buyerName = getString(getColumnIndexOrThrow("name")),
            total = getLong(getColumnIndexOrThrow("total")),
            shipping = getLong(getColumnIndexOrThrow("shipping")),
            status = getString(getColumnIndexOrThrow("status")),
            paymentRef = getString(getColumnIndexOrThrow("payment_ref")),
            city = getString(getColumnIndexOrThrow("city")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            items = items
        )
    }

    companion object {
        private const val KEY_EMAIL = "recovery_email"
        private const val KEY_CODE = "recovery_code"
    }
}
