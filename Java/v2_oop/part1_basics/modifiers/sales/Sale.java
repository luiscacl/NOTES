package v2_oop.part1_basics.modifiers.sales;

import java.math.BigDecimal;
import java.util.Objects;

import v2_oop.part1_basics.modifiers.store.Product;

/*
    Clase de apoyo para AccessModifiers.java (léela desde allá).

    Sale está en OTRO paquete y NO hereda de Product.
    Para ella, Product solo tiene lo public.

    Las líneas comentadas abajo NO compilan, y están así solo por eso:
        producto.adjustStock(-1);     // ERROR: acceso de paquete, Sale está en otro paquete
        producto.tax();           // ERROR: protected, y Sale no es hija de Product
        producto.getBasePrice();      // ERROR: protected
        producto.stock;                // ERROR: private
        new MovementLog();   // ERROR: la clase no es public; desde aquí no existe
*/
public final class Sale {

    private final Product producto;
    private final int cantidad;

    public Sale(Product producto, int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        this.producto = Objects.requireNonNull(producto, "producto");
        this.cantidad = cantidad;
    }

    public BigDecimal total() {
        return producto.finalPrice().multiply(BigDecimal.valueOf(cantidad));
    }
}
