package com.leaftaps.testcases;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.utils.DataLibrary;
import com.framework.utils.TestMetadata;
import com.leaftaps.pages.FindLeadPage;
import com.leaftaps.pages.LoginPage;

@TestMetadata(
		name        = "DeleteLead",
		description = "Verify if the lead has been deleted",
		authors     = "Rajesh",
		category    = "Smoke",
		excelFile   = "DeleteLead"
)
public class TC005_DeleteLead extends ProjectSpecificMethods{

	@DataProvider(name = "deleteLeadData")
	public Object[][] deleteLeadData() {
		return DataLibrary.readExcelData("DeleteLead");
	}

	@Test(dataProvider = "deleteLeadData")
	public void deleteLead(String uname, String pass, String firstName, String errorMsg) {
		FindLeadPage findPage = new LoginPage()
				.enterUsername(uname)
				.enterPassword(pass)
				.clickLogin()
				.clickCrmsfaLink()
				.clickLeadsLink()
				.clickFindLead()
				.enterLeadName(firstName)
				.clickFindleadsButton();

		String firstResultingLead = findPage.getFirstResultingLead();

		findPage.clickFirstResultingLead()
				.clickDeleteLeadLink()
				.clickFindLead()
				.enterLeadID(firstResultingLead)
				.clickOnFindleadsButton()
				.verifyErrorMsg(errorMsg);
	}

	

}
