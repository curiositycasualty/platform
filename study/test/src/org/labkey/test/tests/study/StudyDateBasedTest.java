package org.labkey.test.tests.study;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by RyanS on 5/18/2017.
 */
public class StudyDateBasedTest extends AbstractStudyTimeKeyFieldTest
{
//    @Override
//    protected String getProjectName()
//    {
//        return "DateBasedStudyVerifyProject";
//    }

    protected void doCreateSteps()
    {
        importStudy(DATEBASED_ARCHIVE,null);
    }

    protected void doVerifySteps()
    {
        Map<String,String> kvp = new HashMap<>();
        kvp.put("MouseId","999321033");
        kvp.put("date","2006-02-14");
        testCannotInsertExactDuplicateNoTimeKey(kvp,"CPF-1: Follow-up Chemistry Panel",getFolderName());
        kvp.clear();

        kvp.put("MouseId", "999320518");
        kvp.put("date", "2006-03-04 03:36");
        testCannotInsertDifferingOnlyTimeNoTimeKey(kvp, "AE-1:(VTN) AE Log", getFolderName());
        kvp.clear();

        kvp.put("MouseId", "999320518");
        kvp.put("date", "2006-03-04 time");
        testCanInsertWithOnlyTimeDifferentIfTimeKey(kvp, "AE-1:(VTN) AE Log", getFolderName());
        kvp.clear();

        //TODO: Uncomment and fix after sprint 17.2.3 has branched
        //testCannotUploadDuplicateIfTimeKey(DUPLICATE_DATASET, "APX-1: Abbreviated Physical Exam", getFolderName());

        testCanUploadWithOnlyTimeDifferentIfTimeKey(DIFFERENT_TIME, "APX-1: Abbreviated Physical Exam", getFolderName());

        testCanChangeExtraKeyFromTimeIfDoesNotViolateUnique(getFolderName(), "RCB-1: Reactogenicity-Baseline", DIFFERENT_DATES_DIFFERENT_TIMES);

        testDateFieldDisplaysTimeIfTimeKey();

        testCannotTurnOffExtraTimeKeyIfViolatesUnique(getFolderName(), "RCB-1: Reactogenicity-Baseline");

        testCannotSetAdditionalKeyForDemographics();

        //TODO: no idea how these are meant to work or be set up
        //verifyAliasReplacement();

        //verifyStudyAndDatasets(false);

        //verifyPermissionsRestrictions();

        verifyParticipantReports(26);

        //manageSubjectClassificationTest();

        verifyStudyAndDatasets(false);
    }
}
