/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.export.spi;

import edu.harvard.iq.dataverse.export.ExportException;
import java.io.OutputStream;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author skraffmi
 */
public interface Exporter {
    
    /* When implementing exportDataset, when done writing content, please make sure to flush() the outputStream, 
       but NOT close() it!
       This way an exporter can be used to insert the produced metadata into the 
       body of an HTTP response, etc. (for example, to insert it into the body 
       of an OAI response, where more XML needs to be written, for the outer 
       OAI-PMH record). -- L.A.  4.5
    */
    //public void exportDataset(JsonObject json, OutputStream outputStream) throws ExportException;
    
    public void exportDataset(ExportDataProviderInterface dataProvider, OutputStream outputStream) throws ExportException;
    
    public String getProviderName();
    
    public String getDisplayName();
    
    public default String getPrerequisiteExporterName() {
        return null;
    }
    
    public Boolean isXMLFormat();
    
    public Boolean isHarvestable();
    
    public Boolean isAvailableToUsers();
    
    /* These should throw an ExportException if called on an Exporter that is not isXMLFormat(): */
    public String getXMLNameSpace() throws ExportException;
    public String getXMLSchemaLocation() throws ExportException; 
    public String getXMLSchemaVersion() throws ExportException; 
    
	public default String getMediaType() {
	    return MediaType.APPLICATION_XML;
	};
    
}
