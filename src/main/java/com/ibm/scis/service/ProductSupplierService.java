package com.ibm.scis.service;

import java.io.File;
import java.io.IOException;
import com.ibm.scis.exception.ProcessingException;
import com.ibm.scis.model.ConversionResponse;

public interface ProductSupplierService {

	ConversionResponse convertJsonToCsv(String jsonFileName, File outputDir) throws IOException, ProcessingException;

}
