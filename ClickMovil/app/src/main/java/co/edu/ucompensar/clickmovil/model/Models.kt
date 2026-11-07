package co.edu.ucompensar.clickmovil.model

enum class Role(val label: String) {
    COMPRADOR("Comprador"),
    VENDEDOR("Vendedor"),
    ADMIN("Administrador");

    companion object {
        fun from(raw: String): Role = entries.firstOrNull { it.name == raw } ?: COMPRADOR
    }
}

object Categories {
    val all = listOf("Fundas", "Carga", "Audio", "Soportes", "Energía", "Adaptadores")
}

data class User(
    val id: Long,
    val name: String,
    val email: String,
    val phone: String,
    val city: String,
    val role: Role,
    val active: Boolean
)

data class Product(
    val id: Long,
    val sellerId: Long,
    val name: String,
    val description: String,
    val price: Long,
    val category: String,
    val compatibility: String,
    val stock: Int,
    val imagePath: String?,
    val active: Boolean
)

data class CartLine(
    val itemId: Long,
    val product: Product,
    val quantity: Int
) {
    val subtotal: Long get() = product.price * quantity
}

data class OrderItem(
    val id: Long,
    val orderId: Long,
    val productId: Long,
    val sellerId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long
)

data class StoreOrder(
    val id: Long,
    val code: String,
    val buyerId: Long,
    val buyerName: String,
    val total: Long,
    val shipping: Long,
    val status: String,
    val paymentRef: String,
    val city: String,
    val createdAt: Long,
    val items: List<OrderItem>
)

data class SalesTotals(
    val count: Int,
    val revenue: Long,
    val paid: Int,
    val shipped: Int,
    val delivered: Int,
    val cancelled: Int
)

sealed class OpResult<out T> {
    data class Ok<T>(val value: T) : OpResult<T>()
    data class Err(val message: String) : OpResult<Nothing>()
}
