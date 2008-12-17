/*
 * Copyright (c) 2007-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.study.assay;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpRunTable;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.query.RunDataQueryView;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.common.util.Pair;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jul 11, 2007
 * Time: 9:59:49 AM
 */
public interface AssayProvider extends Handler<ExpProtocol>
{
    PropertyDescriptor[] getUploadSetColumns(ExpProtocol protocol);

    PropertyDescriptor[] getRunPropertyColumns(ExpProtocol protocol);

    PropertyDescriptor[] getRunInputPropertyColumns(ExpProtocol protocol);

    PropertyDescriptor[] getRunDataColumns(ExpProtocol protocol);

    Pair<ExpRun, ExpExperiment> saveExperimentRun(AssayRunUploadContext context, ExpExperiment batch) throws ExperimentException;

    List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles);

    String getName();

    ExpProtocol createAssayDefinition(User user, Container container, String name, String description) throws ExperimentException;

    List<Domain> createDefaultDomains(Container c, User user);

    HttpView getDataDescriptionView(AssayRunUploadForm form);

    Container getAssociatedStudyContainer(ExpProtocol protocol, Object dataId);

    Set<Container> getAllAssociatedStudyContainers(ExpProtocol protocol);

    ActionURL getUploadWizardURL(Container container, ExpProtocol protocol);

    TableInfo createDataTable(UserSchema schema, String alias, ExpProtocol protocol);

    ExpRunTable createRunTable(UserSchema schema, String alias, ExpProtocol protocol);

    FieldKey getParticipantIDFieldKey();

    FieldKey getVisitIDFieldKey(Container targetStudy);

    FieldKey getRunIdFieldKeyFromDataRow();

    FieldKey getDataRowIdFieldKey();

    FieldKey getSpecimenIDFieldKey();

    ActionURL publish(User user, ExpProtocol protocol, Container study, Map<Integer, AssayPublishKey> dataKeys, List<String> errors);

    boolean canPublish();

    List<ParticipantVisitResolverType> getParticipantVisitResolverTypes();

    void setPlateTemplate(Container container, ExpProtocol protocol, PlateTemplate template);

    PlateTemplate getPlateTemplate(Container container, ExpProtocol protocol);

    boolean isPlateBased();

    List<Domain> getDomains(ExpProtocol protocol);

    Set<String> getReservedPropertyNames(ExpProtocol protocol, Domain domain);

    Pair<ExpProtocol, List<Domain>> getAssayTemplate(User user, Container targetContainer);

    Pair<ExpProtocol, List<Domain>> getAssayTemplate(User user, Container targetContainer, ExpProtocol toCopy);

    boolean isFileLinkPropertyAllowed(ExpProtocol protocol, Domain domain);

    boolean isMandatoryDomainProperty(Domain domain, String propertyName);

    boolean allowUpload(User user, Container container, ExpProtocol protocol);

    HttpView getDisallowedUploadMessageView(User user, Container container, ExpProtocol protocol);

    RunDataQueryView createRunDataQueryView(ViewContext context, ExpProtocol protocol);

    RunListQueryView createRunQueryView(ViewContext context, ExpProtocol protocol);

    ModelAndView createRunDataView(ViewContext context, ExpProtocol protocol);

    String getRunListTableName(ExpProtocol protocol);

    String getRunDataTableName(ExpProtocol protocol);

    void deleteProtocol(ExpProtocol protocol, User user) throws ExperimentException;

    /**
     * Get a URL to the assay designer
     * @param container container in which the assay definition should live
     * @param protocol if null, start a new design from scratch. If not null, either the design to edit or the design to copy
     * @param copy if true, create a copy of the protocol that's passed in and start editing it
     * @return
     */
    ActionURL getDesignerURL(Container container, ExpProtocol protocol, boolean copy);

    /**
     * Returns true if the given provider can display a useful details page for dataset data that has been copied.
     * If a provider is a simple GPAT, then it does not have a useful details page
     * @return
     */
    boolean hasUsefulDetailsPage();
}
