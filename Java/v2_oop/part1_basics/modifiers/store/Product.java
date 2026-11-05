package v2_oop.part1_basics.modifiers.store;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/*
    Clase de apoyo para AccessModifiers.java (léela desde allá).

    Product usa los CUATRO niveles de acceso a propósito, cada uno con
    una razón de diseño real:

    | Miembro             | Acceso    | Por qué                                         |
    |---------------------|-----------|-------------------------------------------------|
    | VAT_RATE            | public    | Constante que cualquiera puede necesitar        |
    | constructor         | public    | Cualquiera puede crear productos                |
    | getSku, getStock    | public    | Lectura segura para todos                       |
    | finalPrice          | public    | Lo que el resto del sistema necesita saber      |
    | tax                 | protected | Punto de extensión: las hijas pueden cambiarlo  |
    | getBasePrice        | protected | Herramienta solo para las hijas                 |
    | adjustStock         | (paquete) | Solo Warehouse, del mismo paquete, mueve el stock |
    | sku, basePrice...   | private   | El estado no se toca desde fuera                |
    | validateStock       | private   | Detalle interno                                 |
*/
public class Product {

    public static final BigDecimal VAT_RATE = new BigDecimal("0.16");

    private final String sku;
    private final BigDecimal basePrice;
    private int stock;

    public Product(String sku, BigDecimal basePrice, int initialStock) {
        Objects.requireNonNull(basePrice, "basePrice");
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("El SKU es obligatorio");
        }
        if (basePrice.signum() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor que cero");
        }
        validateStock(initialStock);
        this.sku = sku;
        this.basePrice = basePrice;
        this.stock = initialStock;
    }

    public String getSku() {
        return sku;
    }

    public int getStock() {
        return stock;
    }

    // final: las hijas cambian el impuesto, pero no la fórmula del precio final.
    public final BigDecimal finalPrice() {
        return basePrice.add(tax()).setScale(2, RoundingMode.HALF_UP);
    }

    protected BigDecimal tax() {
        return basePrice.multiply(VAT_RATE);
    }

    protected final BigDecimal getBasePrice() {
        return basePrice;
    }

    // Sin modificador: solo el paquete v2_oop.part1_basics.modifiers.store puede llamarlo.
    void adjustStock(int cambio) {
        int newStock = stock + cambio;
        validateStock(newStock);
        stock = newStock;
    }

    private static void validateStock(int stock) {
        if (stock < 0) {
            throw new IllegalStateException("El stock no puede quedar negativo");
        }
    }
}
