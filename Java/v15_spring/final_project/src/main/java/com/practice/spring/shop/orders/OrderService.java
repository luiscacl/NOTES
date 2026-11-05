package com.practice.spring.shop.orders;

import com.practice.spring.shop.catalog.CatalogService;
import com.practice.spring.shop.orders.OrderDtos.LineRequest;
import com.practice.spring.shop.orders.OrderDtos.OrderResponse;
import com.practice.spring.shop.orders.OrderDtos.OrderSummary;
import com.practice.spring.shop.orders.OrderDtos.PlaceOrderRequest;
import com.practice.spring.shop.security.CurrentUser;
import com.practice.spring.shop.shared.NotFoundException;
import com.practice.spring.shop.shared.PageResponse;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los casos de uso de pedidos. Cada método público es UNA transacción: o pasa todo (existencias
 * apartadas + pedido guardado) o nada. Devuelve DTOs ya armados: con open-in-view apagado, nada
 * se carga después de que la transacción termina.
 */
@Service
class OrderService {

    private final OrderRepository orders;
    private final CatalogService catalog;
    private final Clock clock;

    OrderService(OrderRepository orders, CatalogService catalog, Clock clock) {
        this.orders = orders;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse place(CurrentUser user, PlaceOrderRequest request) {
        Map<Long, Integer> quantities = request.lines().stream()      // el mismo producto dos veces: se suman
                .collect(Collectors.toMap(LineRequest::productId, LineRequest::quantity, Integer::sum));
        List<OrderLine> lines = catalog.reserve(quantities).stream()
                .map(item -> new OrderLine(item.productId(), item.productName(), item.unitPriceCents(), item.quantity()))
                .toList();
        return OrderResponse.from(orders.save(CustomerOrder.place(user.username(), lines, clock.instant())));
    }

    @Transactional(readOnly = true)
    public OrderResponse find(CurrentUser user, long id) {
        return OrderResponse.from(loadVisible(user, id));
    }

    /** El cliente ve los suyos; el administrador, todos. */
    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> list(CurrentUser user, Pageable pageable) {
        return PageResponse.of(user.admin() ? orders.findAll(pageable) : orders.findByCustomer(user.username(), pageable),
                OrderSummary::from);
    }

    @Transactional
    public OrderResponse cancel(CurrentUser user, long id) {
        CustomerOrder order = loadVisible(user, id);
        order.cancel();                                                // la regla vive en el pedido
        order.lines().forEach(line -> catalog.release(line.productId(), line.quantity()));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse ship(long id) {
        CustomerOrder order = load(id);
        order.ship();
        return OrderResponse.from(order);
    }

    /**
     * Un pedido ajeno responde 404, igual que uno inexistente: un 403 le confirmaría al curioso que
     * ese número de pedido EXISTE (part5_security/.../jwt/JwtAndMethodSecurity).
     */
    private CustomerOrder loadVisible(CurrentUser user, long id) {
        CustomerOrder order = load(id);
        if (!user.admin() && !order.belongsTo(user.username())) {
            throw notFound(id);
        }
        return order;
    }

    private CustomerOrder load(long id) {
        return orders.findWithLinesById(id).orElseThrow(() -> notFound(id));
    }

    private static NotFoundException notFound(long id) {
        return new NotFoundException("No existe el pedido " + id);
    }
}
