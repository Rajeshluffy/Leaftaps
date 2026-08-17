package com.leaftaps.testcases;

import org.testng.annotations.Test;

import com.framework.config.data.ConfigurationManager;
import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.utils.TestMetadata;
import com.leaftaps.config.data.LeafTapConfiguration;
import com.leaftaps.pages.LoginPage;

@TestMetadata(
		name        = "VerifyLogin",
		description = "Verify Login functionality with positive data",
		authors     = "Rajesh",
		category    = "Smoke"
)
public class TC001_VerifyLogin extends ProjectSpecificMethods {

	@Test
	public void verifyLogin() {
		LeafTapConfiguration leafTapConfig = ConfigurationManager.getConfiguration(LeafTapConfiguration.class);
		new LoginPage()
				.enterUsername(leafTapConfig.loginUserName())
				.enterPassword(leafTapConfig.loginPassword())
				.clickLogin();
	}
}