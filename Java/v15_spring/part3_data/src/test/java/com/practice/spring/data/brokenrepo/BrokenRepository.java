package com.practice.spring.data.brokenrepo;

import com.practice.spring.data.repositories.SpringDataRepositories.Product;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Un repositorio con un error de dedo, para SpringDataRepositoriesTest (CAPA 3).
 * Vive FUERA del paquete que escanea la aplicación: dentro, impedía que arrancara
 * (que es exactamente lo que enseña esa capa).
 */
public interface BrokenRepository extends Repository<Product, Long> {
    List<Product> findByNmae(String name);
}
