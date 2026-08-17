package com.leaftaps.testcases;

import org.testng.annotations.Test;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.utils.AccountData;
import com.framework.utils.TestMetadata;
import com.leaftaps.pages.LoginPage;

@TestMetadata(
		name        = "VerifyLogOut",
		description = "Verify LogOut functionality with positive data",
		authors     = "Rajesh",
		category    = "Smoke",
		excelFile   = "Login"
)
public class TC002_VerifyLogout extends ProjectSpecificMethods{

	@Test(dataProvider = "fetchData")
	public void runLogout(AccountData account) {
		new LoginPage()
				.enterUsername(account.getUserName())
				.enterPassword(account.getPassword())
				.clickLogin()
				.clickLogOut();
	}

	

	

}
