package v2_oop.part1_basics.modifiers.store;

import java.util.ArrayList;
import java.util.List;

/*
    Clase de apoyo para AccessModifiers.java (léela desde allá).

    Clase SIN public: solo existe para el paquete v2_oop.part1_basics.modifiers.store.
    Desde cualquier otro paquete, ni siquiera se puede escribir su nombre.
    Es un detalle interno de cómo Warehouse hace su trabajo; si mañana se
    reemplaza por una base de datos, nadie fuera del paquete se entera.
*/
final class MovementLog {

    private final List<String> movements = new ArrayList<>();

    void record(String sku, int cambio) {
        movements.add(sku + ":" + cambio);
    }

    int total() {
        return movements.size();
    }
}
