package com.leaftaps.testcases;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.utils.DataLibrary;
import com.framework.utils.TestMetadata;
import com.leaftaps.pages.LoginPage;

@TestMetadata(
		name        = "EditLead",
		description = "Verify whether the existing lead has been edited",
		authors     = "Rajesh",
		category    = "Smoke",
		excelFile   = "EditLead"
)
public class TC004_EditLead extends ProjectSpecificMethods{

	@DataProvider(name = "editLeadData")
	public Object[][] editLeadData() {
		return DataLibrary.readExcelData("EditLead");
	}

	@Test(dataProvider = "editLeadData")
	public void editLead(String uname, String pass, String firstName, String updateComName) {
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
				.clickEditLeadLink()
				.updateCompanyName(updateComName)
				.clickUpdateSubmit()
				.verifyCompanyName(updateComName);
	}

	

}
