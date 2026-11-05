package com.practice.spring.shop.catalog;

import com.practice.spring.shop.shared.BusinessRuleException;
import com.practice.spring.shop.shared.NotFoundException;
import com.practice.spring.shop.shared.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * El catálogo. Es lo ÚNICO público del paquete catalog (junto con sus records): pedidos no ve
 * Product ni ProductRepository, solo estos métodos (v14_architecture/part1_layers/LayeredArchitecture,
 * CAPA 7).
 */
@Service
public class CatalogService {

    /** Lo que se apartó de un producto, con el nombre y el precio de ESE momento. */
    public record ReservedItem(long productId, String productName, long unitPriceCents, int quantity) {
    }

    private final ProductRepository products;

    CatalogService(ProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(Pageable pageable) {
        return PageResponse.of(products.findAll(pageable), ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse find(long id) {
        return ProductResponse.from(load(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        return ProductResponse.from(products.save(new Product(request.name(), request.priceCents(), request.stock())));
    }

    @Transactional
    public ProductResponse update(long id, ProductRequest request) {
        Product product = load(id);
        product.change(request.name(), request.priceCents(), request.stock());
        return ProductResponse.from(product);       // se guarda solo al confirmar (dirty checking)
    }

    /**
     * Aparta las existencias de un pedido completo, o de nada: debe correr DENTRO de la transacción
     * del pedido (MANDATORY falla si no hay una), para que un "no alcanza" a la mitad deshaga lo ya
     * apartado.
     *
     * Los productos se apartan en orden de id (TreeMap): si un pedido aparta A y luego B, y otro B y
     * luego A, cada uno podría quedarse esperando el candado del otro (un deadlock). Con el mismo
     * orden siempre, no pasa.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ReservedItem> reserve(Map<Long, Integer> quantities) {
        return new TreeMap<>(quantities).entrySet().stream().map(line -> {
            Product product = load(line.getKey());
            if (products.takeStock(product.id(), line.getValue()) == 0) {
                throw new BusinessRuleException("INSUFFICIENT_STOCK",
                        "No hay existencias suficientes de '" + product.name() + "'");
            }
            return new ReservedItem(product.id(), product.name(), product.priceCents(), line.getValue());
        }).toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(long productId, int quantity) {
        products.returnStock(productId, quantity);
    }

    private Product load(long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("No existe el producto " + id));
    }
}
