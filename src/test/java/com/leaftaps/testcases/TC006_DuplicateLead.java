package com.leaftaps.testcases;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.utils.DataLibrary;
import com.framework.utils.TestMetadata;
import com.leaftaps.pages.LoginPage;

@TestMetadata(
		name        = "DuplicateLead",
		description = "Verify if the lead is duplicated",
		authors     = "Rajesh",
		category    = "Smoke",
		excelFile   = "DuplicateLead"
)
public class TC006_DuplicateLead extends ProjectSpecificMethods{

	@DataProvider(name = "duplicateLeadData")
	public Object[][] duplicateLeadData() {
		return DataLibrary.readExcelData("DuplicateLead");
	}

	@Test(dataProvider = "duplicateLeadData")
	public void duplicateLead(String uname, String pass, String firstName) {
		new LoginPage()
				.enterUsername(uname)
				.enterPassword(pass)
				.clickLogin()
				.clickCrmsfaLink()
				.clickLeadsLink()
				.clickFindLead()
				.enterLeadName(firstName)
				.clickFindleadsButton()
				.clickFirstResultingLead()
				.clickDuplicateLink()
				.clickCreateLeadDublicate()
				.verifyFirstName(firstName);
	}

	

}
