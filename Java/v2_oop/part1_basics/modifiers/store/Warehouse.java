package v2_oop.part1_basics.modifiers.store;

/*
    Clase de apoyo para AccessModifiers.java (léela desde allá).

    Warehouse es la cara PÚBLICA del paquete para mover inventario.
    Por estar en el MISMO paquete que Product, puede llamar a
    adjustStock (acceso de paquete) y usar MovementLog (clase
    de paquete). Nadie fuera del paquete puede hacer ninguna de las dos
    cosas: tienen que pasar por aquí, y aquí se registra cada movimiento.
*/
public final class Warehouse {

    private final MovementLog movementLog = new MovementLog();

    public void receive(Product producto, int cantidad) {
        requirePositive(cantidad);
        producto.adjustStock(cantidad);
        movementLog.record(producto.getSku(), cantidad);
    }

    public void dispatch(Product producto, int cantidad) {
        requirePositive(cantidad);
        producto.adjustStock(-cantidad);
        movementLog.record(producto.getSku(), -cantidad);
    }

    public int movementCount() {
        return movementLog.total();
    }

    private static void requirePositive(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
    }
}
