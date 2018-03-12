/*
 * Copyright 2014 EUROPEAN DYNAMICS SA <info@eurodyn.com>
 *
 * Licensed under the EUPL, Version 1.1 only (the "License"). You may not use this work except in
 * compliance with the Licence. You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl/licence-eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence
 * is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the Licence for the specific language governing permissions and limitations under
 * the Licence.
 */
package com.eurodyn.qlack2.fuse.ts.impl;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.docx4j.Docx4J;
import org.docx4j.XmlUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.model.structure.HeaderFooterPolicy;
import org.docx4j.model.structure.SectionWrapper;
import org.docx4j.model.table.TblFactory;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.SpreadsheetMLPackage;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.SpreadsheetML.WorksheetPart;
import org.docx4j.openpackaging.parts.WordprocessingML.FooterPart;
import org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.Document;
import org.docx4j.wml.Ftr;
import org.docx4j.wml.Hdr;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Tr;
import org.xlsx4j.sml.CTRst;
import org.xlsx4j.sml.CTSheetFormatPr;
import org.xlsx4j.sml.CTXstringWhitespace;
import org.xlsx4j.sml.Cell;
import org.xlsx4j.sml.Row;
import org.xlsx4j.sml.STCellType;
import org.xlsx4j.sml.SheetData;

import com.eurodyn.qlack2.fuse.ts.api.TemplateService;
import com.eurodyn.qlack2.fuse.ts.exception.QTemplateServiceException;

public class TemplateServiceImpl implements TemplateService {
  

  @Override
  public ByteArrayOutputStream replacePlaceholdersWordDoc(InputStream inputStream,
      Map<String, String> mappings) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {

      WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(inputStream);

      // Replace placeholders on main part.
      replaceBodyPlaceholders(wordMLPackage, mappings);
      // Replace placeholders on header and footer.
      replaceHeaderAndFooterPlaceholders(wordMLPackage, mappings);

      Docx4J.save(wordMLPackage, baos);

    } catch (Docx4JException e) {
      throw new QTemplateServiceException("The document cannot be created!");
    }

    return baos;

  }

  @Override
  public ByteArrayOutputStream createTableInDocxDocument(InputStream inputStream,
      List<String> header, List<LinkedHashMap<Integer, String>> content) {
    ObjectFactory factory = Context.getWmlObjectFactory();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(inputStream);

      int writableWidthTwips = wordMLPackage.getDocumentModel().getSections().get(0)
          .getPageDimensions().getWritableWidthTwips();
      Tbl tblCredProg =
          TblFactory.createTable(0, header.size(), writableWidthTwips / header.size());

      // Add table header (row).
      Tr thead = factory.createTr();
      for (int num = 0; num < header.size(); num++) {
        addTc(thead, header.get(num), wordMLPackage);
      }
      tblCredProg.getContent().add(thead);

      // Add table content (the content is added by row).
      for (Map<Integer, String> c : content) {
        Tr tr = factory.createTr();
        for (Integer column : c.keySet()) {
          addTc(tr, c.get(column), wordMLPackage);
        }
        tblCredProg.getContent().add(tr);
      }

      wordMLPackage.getMainDocumentPart().addObject(tblCredProg);

      Docx4J.save(wordMLPackage, baos);
      
    } catch (Docx4JException e) {
      throw new QTemplateServiceException("The document cannot be created!");
    }
    return baos;
  }

  protected void addTc(Tr tr, String text, WordprocessingMLPackage wordMLPackage) {
    ObjectFactory factory = Context.getWmlObjectFactory();
    Tc tc = factory.createTc();
    tc.getContent().add(wordMLPackage.getMainDocumentPart().createParagraphOfText(text));
    tr.getContent().add(tc);
  }

  private void replaceHeaderPlaceholders(WordprocessingMLPackage wordMLPackage,
      Map<String, String> mappings) {
    List<SectionWrapper> sectionWrappers = wordMLPackage.getDocumentModel().getSections();
    try {
      String xml = null;
      for (SectionWrapper sw : sectionWrappers) {
        HeaderFooterPolicy hfp = sw.getHeaderFooterPolicy();
        if (hfp != null) {
          HeaderPart headerPart = hfp.getFirstHeader();
          if (headerPart != null) {
            xml = XmlUtils.marshaltoString(headerPart.getContents());
          }
          Object obj = null;

          if (xml != null) {
            obj = XmlUtils.unmarshallFromTemplate(xml, mappings);
          }
          // Inject result into docx
          if (obj != null && headerPart != null) {
            headerPart.setJaxbElement((Hdr) obj);
          }
        }
      }
    } catch (JAXBException | Docx4JException e) {
      throw new QTemplateServiceException(
          "Error occured during placeholder replacement on header.");
    }
  }

  private void replaceBodyPlaceholders(WordprocessingMLPackage wordMLPackage,
      Map<String, String> mappings) {
    try {
      MainDocumentPart documentPart = wordMLPackage.getMainDocumentPart();
      documentPart.variableReplace(mappings);
      generateTextWithListedPlaceholder(documentPart);
    } catch (JAXBException | Docx4JException e) {
      throw new QTemplateServiceException(
          "Error occured during placeholder replacement on main body.");
    }
  }

  private void replaceFooterPlaceholders(WordprocessingMLPackage wordMLPackage,
      Map<String, String> mappings) {
    try {
      List<SectionWrapper> sectionWrappers = wordMLPackage.getDocumentModel().getSections();
      String xml = null;
      for (SectionWrapper sw : sectionWrappers) {
        HeaderFooterPolicy hfp = sw.getHeaderFooterPolicy();
        if (hfp != null) {
          FooterPart footerPart = hfp.getFirstFooter();
          if (footerPart != null) {
            xml = XmlUtils.marshaltoString(footerPart.getContents());
          }
          Object obj = null;

          if (xml != null) {
            obj = XmlUtils.unmarshallFromTemplate(xml, mappings);
          }
          // Inject result into docx
          if (obj != null && footerPart != null) {
            footerPart.setJaxbElement((Ftr) obj);

          }
        }
      }
    } catch (JAXBException | Docx4JException e) {
      throw new QTemplateServiceException(
          "Error occured during placeholder replacement on footer.");
    }
  }

  private void replaceHeaderAndFooterPlaceholders(WordprocessingMLPackage wordMLPackage,
      Map<String, String> mappings) {
    replaceHeaderPlaceholders(wordMLPackage, mappings);
    replaceFooterPlaceholders(wordMLPackage, mappings);

  }

  private void generateTextWithListedPlaceholder(MainDocumentPart documentPart) {
    String xml1;
    try {
      xml1 = XmlUtils.marshaltoString(documentPart.getContents(), true);

      Object obj1 = null;
      if (xml1 != null) {
        xml1 = xml1.replaceAll("\n", "</w:t><w:br/><w:t>");
        obj1 = XmlUtils.unmarshalString(xml1);
      }
      if (obj1 != null) {
        documentPart.setJaxbElement((Document) obj1);
      }
    } catch (Docx4JException | JAXBException e) {
      throw new QTemplateServiceException(
          "Text in list on main document part cannot be generated.");
    }
  }
  
  @Override
  public ByteArrayOutputStream generateExcelSpreadsheet(List<String> xlsxHeader,List<LinkedHashMap<Integer, String>> xlsxContent) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    // Create a new spreadsheet package
    SpreadsheetMLPackage pkg;
    try {

      pkg = SpreadsheetMLPackage.createPackage();

      // Create a new worksheet part and retrieve the sheet data
      WorksheetPart sheet =
          pkg.createWorksheetPart(new PartName("/xl/worksheets/sheet1.xml"), "Sheet 1", 1);
      
      setSpreadsheetFormat(sheet);

      addContent(sheet, xlsxContent, xlsxHeader);

      pkg.save(baos);
      
      return baos;


    } catch (JAXBException | Docx4JException e) {
      throw new QTemplateServiceException("The excel document cannot be created!");
    }
  }
  
  private static void addContent(WorksheetPart sheet,
      List<LinkedHashMap<Integer, String>> xlsxContent, List<String> xlsxHeader) {

    // Minimal content already present
    SheetData sheetData;
    try {
      sheetData = sheet.getContents().getSheetData();

      Row rRow = org.xlsx4j.jaxb.Context.getsmlObjectFactory().createRow();
      rRow.setS((long) 0);

      for (int num = 0; num < xlsxHeader.size(); num++) {
        rRow.getC().add(createCell(xlsxHeader.get(num)));
      }
      sheetData.getRow().add(rRow);

      for (LinkedHashMap<Integer, String> map : xlsxContent) {
        Row rColumn = org.xlsx4j.jaxb.Context.getsmlObjectFactory().createRow();
        for (Integer column : map.keySet()) {
          rColumn.getC().add(createCell(map.get(column)));
        }
        sheetData.getRow().add(rColumn);
      }
    } catch (Docx4JException e) {
      throw new QTemplateServiceException("The content of excel spreadsheet cannot be added!");
    }
  }
  
  private static Cell createCell(String content) {

    Cell cell = org.xlsx4j.jaxb.Context.getsmlObjectFactory().createCell();

    CTXstringWhitespace ctx = org.xlsx4j.jaxb.Context.getsmlObjectFactory().createCTXstringWhitespace();
    ctx.setValue(content);
    
    CTRst ctrst = new CTRst();
    ctrst.setT(ctx);

    cell.setT(STCellType.INLINE_STR);
    cell.setIs(ctrst); // add ctrst as inline string
    
    return cell;

  }
  
  private void setSpreadsheetFormat(WorksheetPart sheet) {
    CTSheetFormatPr format = org.xlsx4j.jaxb.Context.getsmlObjectFactory().createCTSheetFormatPr();
    format.setDefaultColWidth(30.0);        
    format.setDefaultRowHeight(16.8);
    format.setCustomHeight(Boolean.TRUE);
    try {
      sheet.getContents().setSheetFormatPr(format);
    } catch (Docx4JException e) {
      throw new QTemplateServiceException("The format of spreadsheet cannot be changed!");
    }
  }

}
