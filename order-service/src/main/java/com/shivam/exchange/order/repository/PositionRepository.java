package com.shivam.exchange.order.repository;

import com.shivam.exchange.order.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Database access for positions (shares owned). Primary key type is Long (the generated id).
 *
 * "Derived queries": Spring reads the METHOD NAME and builds the SQL from it.
 * You never write SQL here; just follow the naming rules:
 *   findBy + field names + And/Or + OrderBy + field + Asc/Desc
 */
public interface PositionRepository extends JpaRepository<Position, Long> {

    /**
     * All of one user's positions, sorted by stock symbol.
     * SQL: SELECT * FROM positions WHERE owner_id = ? ORDER BY symbol ASC
     */
    List<Position> findByOwnerIdOrderBySymbolAsc(String ownerId);

    /**
     * One user's position in one stock (empty if they have none).
     * SQL: SELECT * FROM positions WHERE owner_id = ? AND symbol = ?
     */
    Optional<Position> findByOwnerIdAndSymbol(String ownerId, String symbol);
}
