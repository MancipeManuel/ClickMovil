package co.edu.ucompensar.clickmovil.data

import android.content.Context
import co.edu.ucompensar.clickmovil.model.CartLine
import co.edu.ucompensar.clickmovil.model.OpResult
import co.edu.ucompensar.clickmovil.model.OrderItem
import co.edu.ucompensar.clickmovil.model.Product
import co.edu.ucompensar.clickmovil.model.Role
import co.edu.ucompensar.clickmovil.model.SalesTotals
import co.edu.ucompensar.clickmovil.model.StoreOrder
import co.edu.ucompensar.clickmovil.model.User
import co.edu.ucompensar.clickmovil.util.shippingFor
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * Repositorio cloud de ClickMóvil.
 * Autenticación: Firebase Auth (correo/contraseña).
 * Datos: Cloud Firestore (usuarios, productos, carrito, pedidos).
 * Imágenes: Firebase Storage vía [ImageUploader] con fallback local.
 */
class Repository(context: Context) {
    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val images = ImageUploader(appContext)

    private val users = db.collection("users")
    private val products = db.collection("products")
    private val carts = db.collection("carts")
    private val orders = db.collection("orders")
    private val meta = db.collection("meta")

    suspend fun login(email: String, password: String): User? {
        return try {
            val result = auth.signInWithEmailAndPassword(email.trim().lowercase(), password).await()
            val uid = result.user?.uid ?: return null
            val user = userById(uid) ?: return null
            if (!user.active) {
                auth.signOut()
                return null
            }
            user
        } catch (_: Exception) {
            null
        }
    }

    suspend fun register(
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
        return try {
            val created = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
            val uid = created.user?.uid ?: return OpResult.Err("No se pudo crear la cuenta.")
            val user = User(
                id = uid,
                name = name.trim(),
                email = cleanEmail,
                phone = phone.trim(),
                city = city.trim(),
                role = role,
                active = true
            )
            users.document(uid).set(user.toMap()).await()
            OpResult.Ok(user)
        } catch (e: FirebaseAuthException) {
            OpResult.Err(authMessage(e))
        } catch (e: Exception) {
            OpResult.Err(e.message ?: "No se pudo registrar.")
        }
    }

    suspend fun userById(id: String): User? {
        val snap = users.document(id).get().await()
        if (!snap.exists()) return null
        return snap.toUser(id)
    }

    suspend fun users(): List<User> {
        return users.get().await().documents.mapNotNull { it.toUser(it.id) }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun updateProfile(userId: String, name: String, phone: String, city: String): OpResult<User> {
        if (name.isBlank()) return OpResult.Err("El nombre es obligatorio.")
        users.document(userId).update(
            mapOf(
                "name" to name.trim(),
                "phone" to phone.trim(),
                "city" to city.trim()
            )
        ).await()
        return userById(userId)?.let { OpResult.Ok(it) } ?: OpResult.Err("No se encontró la cuenta.")
    }

    suspend fun setActive(actorId: String, userId: String, active: Boolean): OpResult<Unit> {
        if (actorId == userId) return OpResult.Err("No puedes desactivar tu propia cuenta.")
        val target = userById(userId) ?: return OpResult.Err("Usuario no encontrado.")
        if (target.role == Role.ADMIN && !active && activeAdmins() <= 1) {
            return OpResult.Err("Debe quedar al menos un administrador activo.")
        }
        users.document(userId).update("active", active).await()
        return OpResult.Ok(Unit)
    }

    suspend fun setRole(actorId: String, userId: String, role: Role): OpResult<Unit> {
        val target = userById(userId) ?: return OpResult.Err("Usuario no encontrado.")
        if (actorId == userId && role != Role.ADMIN) {
            return OpResult.Err("No puedes quitarte el rol de administrador.")
        }
        if (target.role == Role.ADMIN && role != Role.ADMIN && activeAdmins() <= 1) {
            return OpResult.Err("Debe quedar al menos un administrador.")
        }
        users.document(userId).update("role", role.name).await()
        return OpResult.Ok(Unit)
    }

    /** Recuperación real con correo de Firebase Auth. */
    suspend fun startRecovery(email: String): OpResult<String> {
        val clean = email.trim().lowercase()
        if (clean.isBlank()) return OpResult.Err("Escribe el correo de la cuenta.")
        return try {
            auth.sendPasswordResetEmail(clean).await()
            OpResult.Ok("Te enviamos un enlace de recuperación a $clean")
        } catch (e: Exception) {
            OpResult.Err(e.message ?: "No se pudo enviar el correo de recuperación.")
        }
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String): OpResult<Unit> {
        // Con Firebase Auth la recuperación se completa desde el correo.
        // Se conserva la firma para no romper la pantalla; el flujo usa el enlace.
        if (newPassword.length < 6) return OpResult.Err("La contraseña debe tener al menos 6 caracteres.")
        return OpResult.Err(
            "Abre el enlace que llegó a $email para definir la nueva contraseña. Código local: $code"
        )
    }

    suspend fun saveManagedUser(
        actorId: String,
        userId: String?,
        name: String,
        email: String,
        password: String,
        phone: String,
        city: String,
        role: Role
    ): OpResult<User> {
        if (userById(actorId)?.role != Role.ADMIN) {
            return OpResult.Err("Solo un administrador puede gestionar cuentas.")
        }
        if (name.isBlank() || email.isBlank()) return OpResult.Err("Nombre y correo son obligatorios.")
        val cleanEmail = email.trim().lowercase()
        if (userId == null) {
            if (password.length < 6) return OpResult.Err("La contraseña debe tener al menos 6 caracteres.")
            return createUserWithSecondaryAuth(name, cleanEmail, password, phone, city, role)
        }
        val target = userById(userId) ?: return OpResult.Err("Usuario no encontrado.")
        if (target.role == Role.ADMIN && role != Role.ADMIN && activeAdmins() <= 1) {
            return OpResult.Err("Debe quedar al menos un administrador.")
        }
        val data = mutableMapOf<String, Any>(
            "name" to name.trim(),
            "email" to cleanEmail,
            "phone" to phone.trim(),
            "city" to city.trim(),
            "role" to role.name
        )
        users.document(userId).update(data).await()
        return userById(userId)?.let { OpResult.Ok(it) } ?: OpResult.Err("No se pudo actualizar el usuario.")
    }

    suspend fun deleteUser(actorId: String, userId: String): OpResult<Unit> {
        if (actorId == userId) return OpResult.Err("No puedes eliminar tu propia cuenta.")
        val target = userById(userId) ?: return OpResult.Err("Usuario no encontrado.")
        if (target.role == Role.ADMIN && activeAdmins() <= 1) {
            return OpResult.Err("Debe quedar al menos un administrador.")
        }
        val hasOrders = orders.whereEqualTo("buyerId", userId).limit(1).get().await().size() > 0
        val sells = products.whereEqualTo("sellerId", userId).limit(1).get().await().size() > 0
        if (hasOrders || sells) {
            return OpResult.Err("Esta cuenta tiene productos o pedidos. Desactívala para conservar la trazabilidad.")
        }
        carts.document(userId).delete().await()
        users.document(userId).delete().await()
        return OpResult.Ok(Unit)
    }

    suspend fun salesTotals(): SalesTotals {
        val list = allOrders()
        return SalesTotals(
            count = list.size,
            revenue = list.filter { it.status != "CANCELADO" }.sumOf { it.total },
            paid = list.count { it.status == "PAGADO" },
            shipped = list.count { it.status == "ENVIADO" },
            delivered = list.count { it.status == "ENTREGADO" },
            cancelled = list.count { it.status == "CANCELADO" }
        )
    }

    suspend fun products(
        category: String?,
        query: String?,
        sellerId: String?,
        includeInactive: Boolean
    ): List<Product> {
        var list = products.get().await().documents.mapNotNull { it.toProduct(it.id) }
        if (!includeInactive) list = list.filter { it.active }
        if (!category.isNullOrBlank()) list = list.filter { it.category == category }
        if (sellerId != null) list = list.filter { it.sellerId == sellerId }
        if (!query.isNullOrBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) || it.compatibility.lowercase().contains(q)
            }
        }
        return list.sortedBy { it.name.lowercase() }
    }

    suspend fun product(id: String): Product? {
        val snap = products.document(id).get().await()
        if (!snap.exists()) return null
        return snap.toProduct(id)
    }

    suspend fun saveProduct(
        actor: User,
        productId: String?,
        name: String,
        description: String,
        price: Long,
        category: String,
        compatibility: String,
        stock: Int,
        imagePath: String?
    ): OpResult<String> {
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
        val id = existing?.id ?: products.document().id
        val remoteImage = if (!imagePath.isNullOrBlank() && imagePath != existing?.imagePath) {
            images.uploadProductImage(imagePath, id)
        } else {
            imagePath ?: existing?.imagePath
        }
        val data = mapOf(
            "sellerId" to sellerId,
            "name" to name.trim(),
            "description" to description.trim(),
            "price" to price,
            "category" to category,
            "compatibility" to compatibility.trim().ifBlank { "Universal" },
            "stock" to stock,
            "imagePath" to remoteImage,
            "active" to true
        )
        products.document(id).set(data, SetOptions.merge()).await()
        return OpResult.Ok(id)
    }

    suspend fun retireProduct(actor: User, productId: String): OpResult<Unit> {
        val existing = product(productId) ?: return OpResult.Err("Producto no encontrado.")
        if (actor.role == Role.VENDEDOR && existing.sellerId != actor.id) {
            return OpResult.Err("No puedes retirar productos de otro vendedor.")
        }
        if (actor.role == Role.COMPRADOR) return OpResult.Err("No tienes permiso para esta acción.")
        products.document(productId).update("active", false).await()
        return OpResult.Ok(Unit)
    }

    suspend fun cart(userId: String): List<CartLine> {
        val snap = carts.document(userId).collection("items").get().await()
        val lines = mutableListOf<CartLine>()
        for (doc in snap.documents) {
            val productId = doc.getString("productId") ?: continue
            val quantity = doc.getLong("quantity")?.toInt() ?: continue
            val product = product(productId) ?: continue
            lines.add(CartLine(itemId = doc.id, product = product, quantity = quantity))
        }
        return lines.sortedBy { it.product.name.lowercase() }
    }

    suspend fun addToCart(userId: String, productId: String, quantity: Int): OpResult<Unit> {
        val product = product(productId) ?: return OpResult.Err("El producto no existe.")
        if (!product.active) return OpResult.Err("Este producto ya no está publicado.")
        if (product.stock <= 0) return OpResult.Err("El producto está agotado.")
        val itemRef = carts.document(userId).collection("items").document(productId)
        val current = itemRef.get().await().getLong("quantity")?.toInt() ?: 0
        val next = current + quantity
        if (next > product.stock) {
            return OpResult.Err("Solo hay ${product.stock} unidades disponibles.")
        }
        itemRef.set(mapOf("productId" to productId, "quantity" to next)).await()
        return OpResult.Ok(Unit)
    }

    suspend fun updateCartQuantity(userId: String, productId: String, quantity: Int): OpResult<Unit> {
        val itemRef = carts.document(userId).collection("items").document(productId)
        if (quantity <= 0) {
            itemRef.delete().await()
            return OpResult.Ok(Unit)
        }
        val product = product(productId) ?: return OpResult.Err("El producto no existe.")
        if (quantity > product.stock) return OpResult.Err("Solo hay ${product.stock} unidades disponibles.")
        itemRef.set(mapOf("productId" to productId, "quantity" to quantity)).await()
        return OpResult.Ok(Unit)
    }

    /**
     * Checkout con pasarela certificada simulada estilo ePayco:
     * genera referencia EPAYCO-xxxxx y deja el pedido en estado PAGADO.
     */
    suspend fun checkout(buyerId: String, city: String): OpResult<StoreOrder> {
        val cityClean = city.trim()
        if (cityClean.isEmpty()) return OpResult.Err("Escribe la ciudad de entrega.")
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
        val orderRef = orders.document()
        val paymentRef = "EPAYCO-${100000 + Random.nextInt(900000)}"
        val code = "CM-${orderRef.id.takeLast(6).uppercase()}"
        val buyer = userById(buyerId)
        val items = lines.map { line ->
            val fresh = product(line.product.id)!!
            mapOf(
                "productId" to fresh.id,
                "sellerId" to fresh.sellerId,
                "productName" to fresh.name,
                "quantity" to line.quantity,
                "unitPrice" to fresh.price
            )
        }
        val orderData = mapOf(
            "code" to code,
            "buyerId" to buyerId,
            "buyerName" to (buyer?.name ?: ""),
            "total" to (subtotal + shipping),
            "shipping" to shipping,
            "status" to "PAGADO",
            "paymentRef" to paymentRef,
            "paymentGateway" to "ePayco",
            "city" to cityClean,
            "createdAt" to System.currentTimeMillis(),
            "items" to items
        )
        db.runTransaction { tx ->
            for (line in lines) {
                val pref = products.document(line.product.id)
                val snap = tx.get(pref)
                val stock = snap.getLong("stock")?.toInt() ?: 0
                if (stock < line.quantity) throw IllegalStateException("Stock insuficiente")
                tx.update(pref, "stock", stock - line.quantity)
            }
            tx.set(orderRef, orderData)
        }.await()
        for (line in lines) {
            carts.document(buyerId).collection("items").document(line.product.id).delete().await()
        }
        return orderById(orderRef.id)?.let { OpResult.Ok(it) }
            ?: OpResult.Err("No se pudo leer el pedido.")
    }

    suspend fun ordersForBuyer(buyerId: String): List<StoreOrder> {
        return orders.whereEqualTo("buyerId", buyerId).get().await().documents
            .mapNotNull { it.toOrder(it.id) }
            .sortedByDescending { it.createdAt }
    }

    suspend fun ordersForSeller(sellerId: String): List<StoreOrder> {
        return allOrders().filter { order -> order.items.any { it.sellerId == sellerId } }
    }

    suspend fun allOrders(): List<StoreOrder> {
        return orders.get().await().documents
            .mapNotNull { it.toOrder(it.id) }
            .sortedByDescending { it.createdAt }
    }

    suspend fun cancelOrder(buyerId: String, orderId: String): OpResult<Unit> {
        val order = orderById(orderId) ?: return OpResult.Err("Pedido no encontrado.")
        if (order.buyerId != buyerId) return OpResult.Err("Solo puedes cancelar tus pedidos.")
        if (order.status != "PAGADO") {
            return OpResult.Err("Solo se cancela un pedido pagado que todavía no fue enviado. El registro se conserva.")
        }
        db.runTransaction { tx ->
            for (item in order.items) {
                val pref = products.document(item.productId)
                val snap = tx.get(pref)
                val stock = snap.getLong("stock")?.toInt() ?: 0
                tx.update(pref, "stock", stock + item.quantity)
            }
            tx.update(orders.document(orderId), "status", "CANCELADO")
        }.await()
        return OpResult.Ok(Unit)
    }

    suspend fun markShipped(sellerId: String, orderId: String): OpResult<Unit> {
        val order = orderById(orderId) ?: return OpResult.Err("Pedido no encontrado.")
        if (order.items.none { it.sellerId == sellerId }) {
            return OpResult.Err("Este pedido no incluye tus productos.")
        }
        if (order.status != "PAGADO") return OpResult.Err("El pedido no está pendiente de envío.")
        orders.document(orderId).update("status", "ENVIADO").await()
        return OpResult.Ok(Unit)
    }

    suspend fun markDelivered(orderId: String): OpResult<Unit> {
        val order = orderById(orderId) ?: return OpResult.Err("Pedido no encontrado.")
        if (order.status == "CANCELADO") return OpResult.Err("Un pedido cancelado no se marca como entregado.")
        orders.document(orderId).update("status", "ENTREGADO").await()
        return OpResult.Ok(Unit)
    }

    suspend fun lowStockCount(sellerId: String): Int {
        return products(null, null, sellerId, includeInactive = false).count { it.stock <= 5 }
    }

    /** Crea cuentas demo y catálogo inicial una sola vez. */
    suspend fun ensureDemoData() {
        val flag = meta.document("seed").get().await()
        if (flag.exists() && flag.getBoolean("done") == true) return
        val sellerId = ensureAuthUser(
            email = "vendedor@correo.com",
            password = "123456",
            name = "TecnoAccesorios",
            phone = "3010000000",
            city = "Bogotá",
            role = Role.VENDEDOR
        )
        ensureAuthUser(
            email = "ana@correo.com",
            password = "123456",
            name = "Ana López",
            phone = "3000000000",
            city = "Bogotá",
            role = Role.COMPRADOR
        )
        ensureAuthUser(
            email = "admin@correo.com",
            password = "admin123",
            name = "Admin Click",
            phone = "3020000000",
            city = "Bogotá",
            role = Role.ADMIN
        )
        if (products.limit(1).get().await().isEmpty && sellerId != null) {
            seedProducts(sellerId)
        }
        meta.document("seed").set(mapOf("done" to true, "at" to FieldValue.serverTimestamp())).await()
    }

    private suspend fun seedProducts(sellerId: String) {
        val catalog = listOf(
            Seed("Funda silicona mate", "Protección contra golpes leves. Material flexible.", 32_900L, "Fundas", "Galaxy A55", 12),
            Seed("Cargador USB-C 20W", "Carga rápida de 20W. Incluye cable corto.", 49_900L, "Carga", "Universal", 8),
            Seed("Audífonos Bluetooth", "Estuche de carga y micrófono. Compatibles con Android.", 79_900L, "Audio", "Universal", 6),
            Seed("Batería externa 10.000 mAh", "Dos salidas. Carga el celular al menos dos veces.", 89_000L, "Energía", "Universal", 5),
            Seed("Cable USB-C 1 m", "Cable de carga y datos. Refuerzo en las puntas.", 19_900L, "Carga", "USB-C", 20),
            Seed("Soporte para carro", "Sujeción en la rejilla del aire.", 25_900L, "Soportes", "Universal", 15),
            Seed("Protector de pantalla", "Vidrio templado. Incluye kit de instalación.", 18_900L, "Fundas", "Galaxy A55", 30),
            Seed("Adaptador USB-C a jack", "Salida de audio de 3.5 mm.", 22_900L, "Adaptadores", "USB-C", 11)
        )
        for (item in catalog) {
            products.document().set(
                mapOf(
                    "sellerId" to sellerId,
                    "name" to item.name,
                    "description" to item.description,
                    "price" to item.price,
                    "category" to item.category,
                    "compatibility" to item.compatibility,
                    "stock" to item.stock,
                    "imagePath" to null,
                    "active" to true
                )
            ).await()
        }
    }

    private suspend fun ensureAuthUser(
        email: String,
        password: String,
        name: String,
        phone: String,
        city: String,
        role: Role
    ): String? {
        val existing = users.whereEqualTo("email", email).limit(1).get().await().documents.firstOrNull()
        if (existing != null) return existing.id
        return when (val created = createUserWithSecondaryAuth(name, email, password, phone, city, role)) {
            is OpResult.Ok -> created.value.id
            is OpResult.Err -> null
        }
    }

    private suspend fun createUserWithSecondaryAuth(
        name: String,
        email: String,
        password: String,
        phone: String,
        city: String,
        role: Role
    ): OpResult<User> {
        return try {
            val secondary = secondaryAuth()
            val created = secondary.createUserWithEmailAndPassword(email, password).await()
            val uid = created.user?.uid ?: return OpResult.Err("No se pudo crear el usuario.")
            secondary.signOut()
            val user = User(uid, name.trim(), email, phone.trim(), city.trim(), role, true)
            users.document(uid).set(user.toMap()).await()
            OpResult.Ok(user)
        } catch (e: FirebaseAuthException) {
            OpResult.Err(authMessage(e))
        } catch (e: Exception) {
            OpResult.Err(e.message ?: "No se pudo crear el usuario.")
        }
    }

    private fun secondaryAuth(): FirebaseAuth {
        val name = "ClickMovilSecondary"
        val app = try {
            FirebaseApp.getInstance(name)
        } catch (_: IllegalStateException) {
            val options = FirebaseApp.getInstance().options
            FirebaseApp.initializeApp(
                appContext,
                FirebaseOptions.Builder()
                    .setApiKey(options.apiKey!!)
                    .setApplicationId(options.applicationId)
                    .setProjectId(options.projectId)
                    .setStorageBucket(options.storageBucket)
                    .build(),
                name
            )
        }
        return FirebaseAuth.getInstance(app)
    }

    private suspend fun orderById(id: String): StoreOrder? {
        val snap = orders.document(id).get().await()
        if (!snap.exists()) return null
        return snap.toOrder(id)
    }

    private suspend fun activeAdmins(): Int {
        return users.whereEqualTo("role", Role.ADMIN.name).whereEqualTo("active", true)
            .get().await().size()
    }

    private fun authMessage(e: FirebaseAuthException): String {
        return when (e.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Ese correo ya está registrado."
            "ERROR_WEAK_PASSWORD" -> "La contraseña debe tener al menos 6 caracteres."
            "ERROR_INVALID_EMAIL" -> "El correo no tiene un formato válido."
            else -> e.message ?: "Error de autenticación."
        }
    }

    private fun User.toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "email" to email,
        "phone" to phone,
        "city" to city,
        "role" to role.name,
        "active" to active
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toUser(id: String): User? {
        val email = getString("email") ?: return null
        return User(
            id = id,
            name = getString("name").orEmpty(),
            email = email,
            phone = getString("phone").orEmpty(),
            city = getString("city").orEmpty(),
            role = Role.from(getString("role").orEmpty()),
            active = getBoolean("active") ?: true
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toProduct(id: String): Product? {
        val name = getString("name") ?: return null
        return Product(
            id = id,
            sellerId = getString("sellerId").orEmpty(),
            name = name,
            description = getString("description").orEmpty(),
            price = getLong("price") ?: 0L,
            category = getString("category").orEmpty(),
            compatibility = getString("compatibility").orEmpty(),
            stock = getLong("stock")?.toInt() ?: 0,
            imagePath = getString("imagePath"),
            active = getBoolean("active") ?: true
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun com.google.firebase.firestore.DocumentSnapshot.toOrder(id: String): StoreOrder? {
        val rawItems = get("items") as? List<Map<String, Any?>> ?: emptyList()
        val items = rawItems.mapIndexed { index, map ->
            OrderItem(
                id = "$id-$index",
                orderId = id,
                productId = map["productId"]?.toString().orEmpty(),
                sellerId = map["sellerId"]?.toString().orEmpty(),
                productName = map["productName"]?.toString().orEmpty(),
                quantity = (map["quantity"] as? Number)?.toInt() ?: 0,
                unitPrice = (map["unitPrice"] as? Number)?.toLong() ?: 0L
            )
        }
        return StoreOrder(
            id = id,
            code = getString("code").orEmpty(),
            buyerId = getString("buyerId").orEmpty(),
            buyerName = getString("buyerName").orEmpty(),
            total = getLong("total") ?: 0L,
            shipping = getLong("shipping") ?: 0L,
            status = getString("status").orEmpty(),
            paymentRef = getString("paymentRef").orEmpty(),
            city = getString("city").orEmpty(),
            createdAt = getLong("createdAt") ?: 0L,
            items = items
        )
    }

    private data class Seed(
        val name: String,
        val description: String,
        val price: Long,
        val category: String,
        val compatibility: String,
        val stock: Int
    )
}
