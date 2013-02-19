/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fb

import org.junit.Test
import org.mockito.Mockito
import org.orbeon.oxf.fb.ContainerOps._
import org.orbeon.oxf.fb.ControlOps._
import org.orbeon.oxf.fb.FormBuilderFunctions._
import org.orbeon.oxf.fb.GridOps._
import org.orbeon.oxf.fb.SectionOps._
import org.orbeon.oxf.fb.ToolboxOps._
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.processor.XFormsServer
import org.orbeon.saxon.dom4j.{NodeWrapper, DocumentWrapper}
import org.orbeon.saxon.om._
import org.orbeon.scaxon.XML._
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar

class FormBuilderFunctionsTest extends DocumentTestBase with AssertionsForJUnit with MockitoSugar {

    val TemplateDoc        = "oxf:/forms/orbeon/builder/form/template.xml"
    val CustomXMLDoc       = "oxf:/org/orbeon/oxf/fb/template-with-custom-xml.xhtml"
    val SectionsGridsDoc   = "oxf:/org/orbeon/oxf/fb/template-with-sections-grids.xhtml"
    val SectionsRepeatsDoc = "oxf:/org/orbeon/oxf/fb/template-with-sections-repeats.xhtml"
    val RowspansDoc        = "oxf:/org/orbeon/oxf/fb/template-with-rowspans.xhtml"

    // Create a form which simulates the main Form Builder model
    def formBuilderDoc(url: String, isCustomInstance: Boolean) =
        elemToDom4j(
            <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
                     xmlns:xf="http://www.w3.org/2002/xforms"
                     xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
                     xmlns:ev="http://www.w3.org/2001/xml-events"
                     xmlns:xbl="http://www.w3.org/ns/xbl">
                <xh:head>
                    <xf:model id="fr-form-model">
                        <xf:instance id="fb-form-instance" src={url} xxf:index="id"/>

                        <!-- TODO: don't duplicate logic with what's in FormBuilderFunctions -->
                        <xf:var name="model"             value="xh:head/xf:model[@id = 'fr-form-model']"/>
                        <xf:var name="metadata-instance" value="$model/xf:instance[@id = 'fr-form-metadata']/*"/>
                        <xf:var name="resources"         value="$model/xf:instance[@id = 'fr-form-resources']/*"/>

                        <xf:instance id="fb-variables">
                            <variables>
                                <selected-cell/>
                            </variables>
                        </xf:instance>

                        <xf:var name="variables"     value="instance('fb-variables')"/>
                        <xf:var name="selected-cell" value="$variables/selected-cell"/>

                        <xf:instance id="fb-components-instance">
                            <components/>
                        </xf:instance>

                        <xf:var name="component-bindings" value="instance('fb-components-instance')//xbl:binding"/>

                        <xf:var name="is-custom-instance" value={if (isCustomInstance) "true()" else "false()"}/>

                        <xf:action ev:event="xforms-model-construct-done">
                            <!-- First store into a temporary document so that multiple inserts won't cause repeat processing until we are done -->
                            <xf:var name="temp" value="xxf:create-document()"/>
                            <xf:insert context="$temp"
                                       origin="xxf:call-xpl('oxf:/forms/orbeon/builder/form/annotate.xpl',
                                                                ('data', 'bindings'),
                                                                (instance(), instance('fb-components-instance')),
                                                                'data')"/>

                            <xf:action type="xpath" xmlns:gridOps="java:org.orbeon.oxf.fb.GridOps">
                                gridOps:initializeGrids($temp)
                            </xf:action>

                            <!--<xf:message level="xxf:log-info" value="saxon:serialize($temp, 'xml')"/>-->

                            <xf:insert ref="instance('fb-form-instance')" origin="$temp"/>
                        </xf:action>
                    </xf:model>
                </xh:head>
                <xh:body>
                </xh:body>
            </xh:html>)

    def formBuilderContainingDocument(url: String, isCustomInstance: Boolean = false) =
        setupDocument(formBuilderDoc(url, isCustomInstance))

    private val control1 = "control-1"
    private val control2 = "control-2"
    private val control3 = "control-3"
    private val section1 = "section-1"
    private val section2 = "section-2"

    @Test def modelInstanceBodyElements(): Unit =
        withActionAndDoc(formBuilderContainingDocument(TemplateDoc)) { doc ⇒
            assert(findModelElement(doc).getDisplayName === "xf:model")
            assert(hasIdValue(findModelElement(doc), "fr-form-model"))

            assert(name(formInstanceRoot(doc) parent * head) === "xf:instance")

            assert(qname(findFRBodyElement(doc)) === (XF → "group"))
        }

    @Test def nameAndId(): Unit =
        withActionAndDoc(formBuilderContainingDocument(TemplateDoc)) { doc ⇒

            // Basic functions
            assert(controlName(controlId(control1)) === control1)
            assert(controlName(bindId(control1)) === control1)

            // Find control element
            assert(qname(findControlByName(doc, control1).get) === (XF → "input"))
            assert(hasIdValue(findControlByName(doc, control1).get, controlId(control1)))
        }

    @Test def controlElements(): Unit =
        withActionAndDoc(formBuilderContainingDocument(TemplateDoc)) { doc ⇒

            // Find bind element
            assert(qname(findBindByName(doc, control1).get) === (XF → "bind"))
            assert(hasIdValue(findBindByName(doc, control1).get, bindId(control1)))

            // Check content of value holder
            assert(findDataHolders(doc, control1).length == 1)
            assert(findDataHolders(doc, control1).head.getStringValue === "")

            // TODO
            // controlResourceHolders
        }

    @Test def sectionName(): Unit =
        withActionAndDoc(formBuilderContainingDocument(TemplateDoc)) { doc ⇒
            assert(findSectionName(doc, control1).get === section1)
            assert(getControlNameOption(doc \\ "*:section" head).get === section1)
        }

    @Test def newBinds(): Unit =
        withActionAndDoc(formBuilderContainingDocument(TemplateDoc)) { doc ⇒
            ensureBinds(doc, Seq(section1, control2))

            assert(qname(findBindByName(doc, control2).get) === (XF → "bind"))
            assert(hasIdValue(findBindByName(doc, control2).get, bindId(control2)))

            ensureBinds(doc, Seq(section2, "grid-1", control3))

            assert(qname(findBindByName(doc, control3).get) === (XF → "bind"))
            assert(hasIdValue(findBindByName(doc, control3).get, bindId(control3)))
        }

    @Test def findNextId(): Unit =
        withActionAndDoc(formBuilderContainingDocument(TemplateDoc)) { doc ⇒
            assert(nextId(doc, "control") === "control-3-control")
            assert(nextId(doc, "section") === "section-3-section")

            // TODO: test more collisions
        }

    @Test def containers(): Unit =
        withActionAndDoc(formBuilderContainingDocument(TemplateDoc)) { doc ⇒
            val firstTd = findFRBodyElement(doc) \\ "*:grid" \\ "*:td" head

            val containers = findAncestorContainers(firstTd)

            assert(localname(containers(0)) === "grid")
            assert(localname(containers(1)) === "section")

            assert(findContainerNames(firstTd) === Seq("section-1"))
        }

    // Select the first grid td (assume there is one)
    def selectFirstTd(doc: NodeInfo): Unit =
        selectTd(findFRBodyElement(doc) \\ "*:grid" \\ "*:td" head)

    @Test def insertControl(): Unit = insertControl(isCustomInstance = false)
    @Test def insertControlCustomXML(): Unit = insertControl(isCustomInstance = true)

    private def insertControl(isCustomInstance: Boolean): Unit =
        withActionAndDoc(formBuilderContainingDocument(TemplateDoc, isCustomInstance)) { doc ⇒

            val binding = <binding element="xf|input" xmlns:xf="http://www.w3.org/2002/xforms"/>

            // Insert a new control into the next empty td
            selectFirstTd(doc)
            val newControlNameOption = insertNewControl(doc, binding)

            // Check the control's name
            assert(newControlNameOption === Some("control-3"))
            val newControlName = newControlNameOption.get

            // Test result
            assert(hasIdValue(findControlByName(doc, newControlName).get, controlId(newControlName)))

            val newlySelectedTd = findSelectedTd(doc)
            assert(newlySelectedTd.isDefined)
            assert(newlySelectedTd.get \ * \@ "id" === controlId(newControlName))

            val containerNames = findContainerNames(newlySelectedTd.get)
            assert(containerNames == Seq("section-1"))

            // NOTE: We should maybe just compare the XML for holders, binds, and resources
            val dataHolder = assertDataHolder(doc, newControlName, isCustomInstance)
            if (! isCustomInstance)
                assert(name(dataHolder.head precedingSibling * head) === "control-1")

            val controlBind = findBindByName(doc, newControlName).get
            assert(hasIdValue(controlBind, bindId(newControlName)))
            assert((controlBind precedingSibling * att "id") === bindId("control-1"))

            assert(formResourcesRoot \ "resource" \ newControlName nonEmpty)
        }

    @Test def insertRepeat(): Unit = insertRepeat(isCustomInstance = false)
    @Test def insertRepeatCustomXML(): Unit = insertRepeat(isCustomInstance = true)

    private def insertRepeat(isCustomInstance: Boolean): Unit =
        withActionAndDoc(formBuilderContainingDocument(TemplateDoc, isCustomInstance)) { doc ⇒

            // Insert a new repeated grid after the current grid
            selectFirstTd(doc)
            val newRepeatNameOption = insertNewRepeat(doc)

            assert(newRepeatNameOption === Some("grid-3"))
            val newRepeatName = newRepeatNameOption.get

            locally {

                val newlySelectedTd = findSelectedTd(doc)
                assert(newlySelectedTd.isDefined)
                assert((newlySelectedTd flatMap (_ parent * headOption) flatMap (_ parent * headOption) head) \@ "id" === gridId(newRepeatName))

                val containerNames = findContainerNames(newlySelectedTd.get)
                assert(containerNames === Seq("section-1", newRepeatName))

                // NOTE: We should maybe just compare the XML for holders, binds, and resources
                val dataHolder = assertDataHolder(doc, containerNames.last, isCustomInstance)
                if (! isCustomInstance)
                    assert(name(dataHolder.head precedingSibling * head) === "control-1")

                val controlBind = findBindByName(doc, newRepeatName).get
                assert(hasIdValue(controlBind, bindId(newRepeatName)))
                assert((controlBind precedingSibling * att "id") === bindId("control-1"))

                assert(findModelElement(doc) \ "*:instance" filter(hasIdValue(_, "grid-3-template")) nonEmpty)
            }

            // Insert a new control
            val binding = <binding element="xf|input" xmlns:xf="http://www.w3.org/2002/xforms"/>
            val newControlNameOption = insertNewControl(doc, binding)

            assert(newControlNameOption === Some(if (isCustomInstance) "control-3" else "control-4"))
            val newControlName = newControlNameOption.get

            // Test result
            locally {

                val newlySelectedTd = findSelectedTd(doc)
                assert(newlySelectedTd.isDefined)
                assert(newlySelectedTd.get \ * \@ "id" === controlId(newControlName))

                val containerNames = findContainerNames(newlySelectedTd.get)
                assert(containerNames === Seq("section-1", newRepeatName))

                assert(hasIdValue(findControlByName(doc, newControlName).get, controlId(newControlName)))

                // NOTE: We should maybe just compare the XML for holders, binds, and resources
                val dataHolder = assertDataHolder(doc, newControlName, isCustomInstance)
                if (! isCustomInstance) {
                    assert(dataHolder.head precedingSibling * isEmpty)
                    assert(name(dataHolder.head parent * head) === newRepeatName)
                }

                val controlBind = findBindByName(doc, newControlName).get
                assert(hasIdValue(controlBind, bindId(newControlName)))
                assert(hasIdValue(controlBind parent * head, bindId(newRepeatName)))

                assert(formResourcesRoot \ "resource" \ newControlName nonEmpty)

                val templateHolder = templateRoot(doc, newRepeatName).get \ newControlName headOption

                if (! isCustomInstance) {
                    assert(templateHolder.isDefined)
                    assert(templateHolder.get precedingSibling * isEmpty)
                    assert(name(templateHolder.get parent * head) === newRepeatName)
                } else {
                    assert(templateHolder.isEmpty)
                }
            }
        }

    def compareExpectedCells(grid: NodeInfo, expected: Seq[Seq[Cell]]): Unit = {
        val trs = grid \ "tr"
        for ((expected, index) ← expected.zipWithIndex)
            yield assert(getRowCells(trs(index)) === expected)
    }

    @Test def rowspanGetRowCells(): Unit =
        withActionAndDoc(formBuilderContainingDocument(RowspansDoc)) { doc ⇒

            val grid = doc \\ "grid" head

            def td(id: String) = grid \\ * filter (hasIdValue(_, id)) head

            val expected = Seq(
                Seq(Cell(td("11"), 1, false), Cell(td("12"), 2, false), Cell(td("13"), 1, false)),
                Seq(Cell(td("21"), 2, false), Cell(td("12"), 1, true),  Cell(td("23"), 1, false)),
                Seq(Cell(td("21"), 1, true),  Cell(td("32"), 1, false), Cell(td("33"), 1, false))
            )

            compareExpectedCells(grid, expected)
        }

    def rewrap(node: NodeInfo) = node match {
        case nodeWrapper: NodeWrapper ⇒ node.root.asInstanceOf[DocumentWrapper].wrap(nodeWrapper.getUnderlyingNode)
        case _ ⇒ node
    }

    @Test def rowspanInsertRowBelow(): Unit =
        withActionAndDoc(formBuilderContainingDocument(RowspansDoc)) { doc ⇒

            val grid = doc \\ "grid" head

            // Insert one row below each existing row
            for (tr ← grid \ "tr" toList)
                insertRowBelow(rewrap(tr)) // rewrap after mutation (it's dangerous to play with NodeInfo and mutation!)

            def td(id: String) = grid \\ * filter (hasIdValue(_, id)) head

            val expected = Seq(
                Seq(Cell(td("11"),        1, false), Cell(td("12"),        3, false), Cell(td("13"),        1, false)),
                Seq(Cell(td("tmp-2-tmp"), 1, false), Cell(td("12"),        2, true),  Cell(td("tmp-3-tmp"), 1, false)),
                Seq(Cell(td("21"),        3, false), Cell(td("12"),        1, true),  Cell(td("23"),        1, false)),
                Seq(Cell(td("21"),        2, true),  Cell(td("tmp-4-tmp"), 1, false), Cell(td("tmp-5-tmp"), 1, false)),
                Seq(Cell(td("21"),        1, true),  Cell(td("32"),        1, false), Cell(td("33"),        1, false)),
                Seq(Cell(td("tmp-6-tmp"), 1, false), Cell(td("tmp-7-tmp"), 1, false), Cell(td("tmp-8-tmp"), 1, false))
            )

            compareExpectedCells(grid, expected)
        }

    def sectionWithGridAndControls: NodeInfo =
        <fr:section xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                    xmlns:xh="http://www.w3.org/1999/xhtml"
                    xmlns:xf="http://www.w3.org/2002/xforms">
            <fr:grid>
                <xh:tr><xh:td><xf:input id="control-11-control"/></xh:td></xh:tr>
            </fr:grid>
            <fr:grid id="grid-2-grid">
                <xh:tr><xh:td><xf:input id="control-21-control"/></xh:td></xh:tr>
            </fr:grid>
            <fr:grid>
                <xh:tr><xh:td><xf:input id="control-31-control"/></xh:td></xh:tr>
            </fr:grid>
        </fr:section>

    @Test def testPrecedingNameForControl(): Unit = {

        val section = sectionWithGridAndControls

        val controls = section \\ "*:td" \ * filter (_ \@ "id" nonEmpty)

        val expected = Seq(None, Some("grid-2"), Some("grid-2"))
        val actual = controls map (precedingControlNameInSectionForControl(_))

        assert(actual === expected)
    }

    @Test def testPrecedingNameForGrid(): Unit = {

        val section = sectionWithGridAndControls

        val grids = section \\ "*:grid"

        val expected = Seq(Some("control-11"), Some("grid-2"), Some("control-31"))
        val actual = grids map (precedingControlNameInSectionForGrid(_, includeSelf = true))

        assert(actual === expected)
    }

    def assertSelectedTdAfterDelete(beforeAfter: Seq[(String, String)])(delete: NodeInfo ⇒ Any): Unit = {

        // For before/after td ids: create a doc, call the delete function, and assert the resulting selected td
        def deleteRowCheckSelectedTd(beforeTdId: String, afterTdId: String) =
            withActionAndDoc(formBuilderContainingDocument(SectionsGridsDoc)) { doc ⇒

                def getTd(id: String) = doc \\ "*:td" find (hasIdValue(_, id)) head

                val beforeTd = getTd(beforeTdId)
                selectTd(beforeTd)
                delete(beforeTd)

                val actualSelectedId = findSelectedTd(doc) map (_ \@ "id" stringValue)

                assert(actualSelectedId === Some(afterTdId))
            }

        // Test all
        for ((beforeTdId, afterTdId) ← beforeAfter)
            deleteRowCheckSelectedTd(beforeTdId, afterTdId)
    }

    @Test def selectedTdAfterDeletedRow(): Unit = {

        // A few before/after ids of selected tds
        val beforeAfter = Seq(
            "1111" → "1121",    // first td
            "2222" → "2231",    // middle td
            "3333" → "3323",    // last td
            "2111" → "2121"     // first td of grid/section
        )

        assertSelectedTdAfterDelete(beforeAfter) { td ⇒
            deleteRow(td parent * head)
        }
    }

    @Test def selectedTdAfterDeletedCol(): Unit = {

        // A few before/after ids of selected tds
        val beforeAfter = Seq(
            "1111" → "1112",    // first td
            "2222" → "2223",    // middle td
            "3333" → "3332",    // last td
            "2111" → "2112"     // first td of grid/section
        )

        assertSelectedTdAfterDelete(beforeAfter) { td ⇒
            deleteCol(getColTds(td) head)
        }
    }

    @Test def selectedTdAfterDeletedGrid(): Unit = {

        // A few before/after ids of selected tds
        val beforeAfter = Seq(
            "1111" → "1211",    // first td
            "2222" → "2311",    // middle td
            "3333" → "3233",    // last td
            "2111" → "2211"     // first td of grid/section
        )

        assertSelectedTdAfterDelete(beforeAfter) { td ⇒
            deleteContainer(getContainingGrid(td))
        }
    }

    @Test def selectedTdAfterDeletedSection(): Unit = {

        // A few before/after ids of selected tds
        val beforeAfter = Seq(
            "1111" → "2111",    // first td
            "2222" → "3111",    // middle td
            "3333" → "2333",    // last td
            "2111" → "3111"     // first td of grid/section
        )

        assertSelectedTdAfterDelete(beforeAfter) { td ⇒
            deleteContainer(findAncestorContainers(getContainingGrid(td)) head)
        }
    }

    @Test def lastGridInSectionAndCanInsert(): Unit =
        withActionAndDoc(formBuilderContainingDocument(TemplateDoc)) { doc ⇒

            // Initially can insert all
            assert(canInsertSection(doc) === true)
            assert(canInsertGrid(doc)    === true)
            assert(canInsertControl(doc) === true)

            // Remove everything (assume top-level section with a single grid inside)
            childrenContainers(findFRBodyElement(doc)).toList foreach  { section ⇒ // evaluate with toList otherwise the lazy iterator can fail
                assert(isLastGridInSection(childrenGrids(section).head) === true)
                deleteContainer(section)
            }

            // After everything is removed we can only insert a section (later: can also insert grid)
            assert(canInsertSection(doc) === true)
            assert(canInsertGrid(doc)    === false)
            assert(canInsertControl(doc) === false)
        }

    @Test def customXMLBindRef(): Unit =
        withActionAndDoc(formBuilderContainingDocument(CustomXMLDoc, isCustomInstance = true)) { doc ⇒

            def rawBindRef(inDoc: NodeInfo, name: String) =
                findBindByName(inDoc, name) flatMap
                    bindRefOrNodeset

            // Automatic de-annotation
            assert(DataModel.getBindRef(doc, "control-1") === Some("control-1"))
            // Raw bind ref is annotated
            assert(Some(DataModel.annotatedBindRef(bindId("control-1"), "control-1")) === rawBindRef(doc, "control-1"))
            // "Annotate if needed" works
            assert(DataModel.annotatedBindRef(bindId("control-1"), "control-1") === DataModel.annotatedBindRefIfNeeded(bindId("control-1"), "control-1"))
        }

    @Test def allowedBindingExpressions(): Unit = {

        val doc = this setupDocument
            <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
                     xmlns:xh="http://www.w3.org/1999/xhtml"
                     xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
                <xh:head>
                    <xf:model xxf:xpath-analysis="true">

                        <xf:instance id="fr-form-instance">
                            <form>
                                <section-1>
                                    <control-1/>
                                </section-1>
                            </form>
                        </xf:instance>

                        <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
                            <xf:bind id="section-1-bind" name="section-1" ref="section-1">
                                <xf:bind id="control-1-bind" name="control-1" ref="control-1"/>
                            </xf:bind>
                        </xf:bind>
                    </xf:model>
                </xh:head>
                <xh:body>
                    <xf:group id="section-1-section" bind="section-1-bind">
                        <xf:input id="control-1-control" bind="control-1-bind"/>
                    </xf:group>
                </xh:body>
            </xh:html>

        withContainingDocument(doc) {
            val section1 = doc.getObjectByEffectiveId("section-1-section").asInstanceOf[XFormsControl]
            val control1 = doc.getObjectByEffectiveId("control-1-control").asInstanceOf[XFormsControl]

            assert(true  === DataModel.isAllowedBindingExpression(section1, "section-1")) // existing node
            assert(false === DataModel.isAllowedBindingExpression(section1, "foo/bar"))   // non-existing node
            assert(false === DataModel.isAllowedBindingExpression(section1, "("))         // invalid expression
            assert(true  === DataModel.isAllowedBindingExpression(section1, "/"))         // root node
            assert(true  === DataModel.isAllowedBindingExpression(section1, ".."))        // complex content

            assert(true  === DataModel.isAllowedBindingExpression(control1, "control-1")) // existing node
            assert(false === DataModel.isAllowedBindingExpression(control1, "foo/bar"))   // non-existing node
            assert(false === DataModel.isAllowedBindingExpression(control1, "("))         // invalid expression
            assert(false === DataModel.isAllowedBindingExpression(control1, "/"))         // root node
            assert(false === DataModel.isAllowedBindingExpression(control1, ".."))        // complex content
        }
    }

    @Test def controlEffectiveId(): Unit =
        withActionAndDoc(formBuilderContainingDocument(SectionsRepeatsDoc)) { doc ⇒

            val expected = Map(
                "|fb$section-1-section$tmp-11-tmp$control-1-control|"                      → "control-1-control",
                "|fb$section-1-section$grid-4-grid$control-5-control·1|"                   → "control-5-control",
                "|fb$section-1-section$section-3-section$tmp-12-tmp$control-6-control|"    → "control-6-control",
                "|fb$section-1-section$section-3-section$grid-7-grid$control-8-control·1|" → "control-8-control"
            )

            for ((expected, id) ← expected)
                assert(Some(expected) === buildControlAbsoluteId(doc, id))
        }

//    @Test def insertHolders() {
//
//    }
//
//    @Test def ensureDataHolder() {
//
//    }
//
//    @Test def renameControl() {
//
//    }
//
//    @Test def ensureEmptyTd() {
//
//    }
//
//    @Test def insertRowBelow() {
//
//    }
//
//    @Test def renameBind() {
//
//    }

    private def assertDataHolder(doc: DocumentWrapper, holderName: String, isCustomInstance: Boolean) = {
        val dataHolder = findDataHolders(doc, holderName)
        if (isCustomInstance)
            assert(dataHolder.isEmpty)
        else
            assert(dataHolder.length == 1)
        dataHolder
    }

    private def withActionAndDoc[T](doc: XFormsContainingDocument)(body: DocumentWrapper ⇒ T) {
        val actionInterpreter = mockActionInterpreter(doc)
        withScalaAction(actionInterpreter) {
            withContainingDocument(doc) {
                body(doc.models find (_.getId == "fr-form-model") flatMap (m ⇒ Option(m.getInstance("fb-form-instance"))) map (_.documentInfo.asInstanceOf[DocumentWrapper]) orNull)
            }
        }
    }

    private def mockActionInterpreter(doc: XFormsContainingDocument) = {
        val actionInterpreter = mock[XFormsActionInterpreter]
        Mockito when actionInterpreter.containingDocument thenReturn doc
        Mockito when actionInterpreter.container thenReturn doc
        Mockito when actionInterpreter.indentedLogger thenReturn new IndentedLogger(XFormsServer.logger, "action")

        actionInterpreter
    }
}