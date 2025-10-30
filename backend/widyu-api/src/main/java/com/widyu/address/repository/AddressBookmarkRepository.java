package com.widyu.address.repository;

import com.widyu.addressbookmark.AddressBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressBookmarkRepository extends JpaRepository<AddressBookmark, Long> {
}
