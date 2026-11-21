package co.edu.ucompensar.clickmovil.model

/** Rol de la cuenta. Define el menú y las operaciones permitidas. */
enum class Role(val label: String) {
    COMPRADOR("Comprador"),
    VENDEDOR("Vendedor"),
    ADMIN("Administrador");

    companion object {
        /** Convierte el texto guardado en Firestore al rol. Si no coincide, usa comprador. */
        fun from(raw: String): Role = entries.firstOrNull { it.name == raw } ?: COMPRADOR
    }
}

/** Categorías fijas del catálogo de accesorios. */
object Categories {
    val all = listOf("Fundas", "Carga", "Audio", "Soportes", "Energía", "Adaptadores")
}

/** Perfil de negocio guardado en Firestore. La contraseña vive solo en Firebase Auth. */
data class User(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val city: String,
    val role: Role,
    val active: Boolean
)

/** Publicación de un vendedor. imagePath puede ser URL de Storage, ruta local o nulo. */
data class Product(
    val id: String,
    val sellerId: String,
    val name: String,
    val description: String,
    val price: Long,
    val category: String,
    val compatibility: String,
    val stock: Int,
    val imagePath: String?,
    val active: Boolean
)

/** Línea del carrito ya unida con el producto para calcular el subtotal. */
data class CartLine(
    val itemId: String,
    val product: Product,
    val quantity: Int
) {
    val subtotal: Long get() = product.price * quantity
}

/** Copia del producto dentro de un pedido, para conservar precio y vendedor. */
data class OrderItem(
    val id: String,
    val orderId: String,
    val productId: String,
    val sellerId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long
)

/** Pedido confirmado. paymentRef es la referencia generada en el checkout. */
data class StoreOrder(
    val id: String,
    val code: String,
    val buyerId: String,
    val buyerName: String,
    val total: Long,
    val shipping: Long,
    val status: String,
    val paymentRef: String,
    val city: String,
    val createdAt: Long,
    val items: List<OrderItem>
)

/** Conteos del reporte de ventas. revenue excluye los pedidos cancelados. */
data class SalesTotals(
    val count: Int,
    val revenue: Long,
    val paid: Int,
    val shipped: Int,
    val delivered: Int,
    val cancelled: Int
)

/** Resultado de una operación de negocio: valor correcto o mensaje para el usuario. */
sealed class OpResult<out T> {
    data class Ok<T>(val value: T) : OpResult<T>()
    data class Err(val message: String) : OpResult<Nothing>()
}
