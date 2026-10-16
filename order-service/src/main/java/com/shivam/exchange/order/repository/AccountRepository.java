package com.shivam.exchange.order.repository;

import com.shivam.exchange.order.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for accounts. This is ONLY an interface: Spring Data JPA writes the
 * implementation (and the SQL) for you when the app starts.
 *
 * JpaRepository<Account, String> means: entity = Account, primary key type = String (ownerId).
 * You get these for free, among others:
 *   save(account)          INSERT or UPDATE
 *   findById("shivam")     SELECT ... WHERE owner_id = 'shivam'   (returns Optional)
 *   existsById("shivam")   SELECT COUNT(*) ... > 0
 *   findAll(), deleteById(...), count()
 */
public interface AccountRepository extends JpaRepository<Account, String> {
}
