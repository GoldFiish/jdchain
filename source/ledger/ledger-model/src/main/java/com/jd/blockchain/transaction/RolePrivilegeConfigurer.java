package com.jd.blockchain.transaction;

import com.jd.blockchain.ledger.LedgerPermission;
import com.jd.blockchain.ledger.TransactionPermission;

public interface RolePrivilegeConfigurer {
	
	String getRoleName();
	
	RolePrivilegeConfigurer disable(TransactionPermission... permissions);

	RolePrivilegeConfigurer enable(TransactionPermission... permissions);

	RolePrivilegeConfigurer disable(LedgerPermission... permissions);

	RolePrivilegeConfigurer enable(LedgerPermission... permissions);

	RolePrivilegeConfigurer configure(String roleName);
}
