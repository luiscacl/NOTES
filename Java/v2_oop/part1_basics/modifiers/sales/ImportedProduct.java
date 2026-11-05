package v2_oop.part1_basics.modifiers.sales;

import java.math.BigDecimal;
import java.util.Objects;

import v2_oop.part1_basics.modifiers.store.Product;

/*
    Clase de apoyo para AccessModifiers.java (léela desde allá).

    ImportedProduct está en OTRO paquete, pero SÍ hereda de Product.
    Por eso puede usar lo protected de Product: tax() y
    getBasePrice(). Lo de paquete y lo private siguen cerrados para ella.
*/
public final class ImportedProduct extends Product {

    private final BigDecimal tariffRate;

    public ImportedProduct(String sku, BigDecimal basePrice, int initialStock, BigDecimal tariffRate) {
        super(sku, basePrice, initialStock);
        this.tariffRate = Objects.requireNonNull(tariffRate, "tariffRate");
    }

    // Sobrescribe un método protected: el IVA del padre + un arancel de importación.
    @Override
    protected BigDecimal tax() {
        return super.tax().add(getBasePrice().multiply(tariffRate));
    }

    /*
        La regla fina de protected (que casi nadie conoce):
        desde otro paquete, una hija puede usar lo protected de SU PROPIA
        parte heredada (this, super, o referencias de su mismo tipo),
        pero NO lo protected de un objeto Product cualquiera.

            BigDecimal impuestoDe(Product otro) {
                return otro.tax();     // ERROR: otro podría ser un Product ajeno
            }

            BigDecimal impuestoDe(ImportedProduct otro) {
                return otro.tax();     // OK: es de mi mismo tipo
            }

        Motivo: protected es un acuerdo entre el padre y SUS hijas para
        extenderlo, no una puerta para que una hija manipule objetos de
        otras jerarquías.

        Y adjustStock (acceso de paquete) tampoco se puede usar aquí:
            adjustStock(5);                // ERROR: heredar no da acceso de paquete
    */
}
