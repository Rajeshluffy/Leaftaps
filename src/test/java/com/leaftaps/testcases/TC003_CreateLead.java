package com.leaftaps.testcases;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.utils.DataLibrary;
import com.framework.utils.TestMetadata;
import com.leaftaps.pages.LoginPage;

@TestMetadata(
		name        = "CreateLead",
		description = "Verify that the lead is created",
		authors     = "Rajesh",
		category    = "Smoke",
		excelFile   = "CreateLead"
)
public class TC003_CreateLead extends ProjectSpecificMethods{

	/**
	 * CreateLead needs 5 columns (uname, pass, companyName, firstName, lastName),
	 * which don't fit the shared {@code fetchData} provider's 4-column AccountData
	 * model (accountId, userName, password, url) — so this reads
	 * {@code data/CreateLead.xlsx} directly, same pattern as alfaDock's
	 * BOMDownload custom DataProvider.
	 */
	@DataProvider(name = "createLeadData")
	public Object[][] createLeadData() {
		return DataLibrary.readExcelData("CreateLead");
	}

	@Test(dataProvider = "createLeadData")
	public void createLead(String uname, String pass, String companyName, String firstName, String lastName) {
		new LoginPage()
				.enterUsername(uname)
				.enterPassword(pass)
				.clickLogin()
				.clickCrmsfaLink()
				.clickLeadsLink()
				.clickCreateLeadLink()
				.enterCompanyName(companyName)
				.enterFirstName(firstName)
				.enterLastName(lastName)
				.clickCreateLeadButton()
				.verifyFirstName(firstName);
	}

	
}
