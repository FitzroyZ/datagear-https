/*
 * Copyright (c) 2018 datagear.org. All Rights Reserved.
 */

package org.datagear.web.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.cometd.bayeux.server.ServerChannel;
import org.datagear.connection.ConnectionSource;
import org.datagear.dataexchange.BatchDataExchange;
import org.datagear.dataexchange.BatchDataExchangeContext;
import org.datagear.dataexchange.ConnectionFactory;
import org.datagear.dataexchange.DataExchange;
import org.datagear.dataexchange.DataExchangeService;
import org.datagear.dataexchange.DataFormat;
import org.datagear.dataexchange.DataImportOption;
import org.datagear.dataexchange.DataSourceConnectionFactory;
import org.datagear.dataexchange.FileReaderResourceFactory;
import org.datagear.dataexchange.FileWriterResourceFactory;
import org.datagear.dataexchange.Query;
import org.datagear.dataexchange.ResourceFactory;
import org.datagear.dataexchange.SimpleBatchDataExchange;
import org.datagear.dataexchange.SqlQuery;
import org.datagear.dataexchange.SubDataExchange;
import org.datagear.dataexchange.TableQuery;
import org.datagear.dataexchange.TextDataExportOption;
import org.datagear.dataexchange.TextValueDataImportOption;
import org.datagear.dataexchange.support.CsvDataExport;
import org.datagear.dataexchange.support.CsvDataImport;
import org.datagear.dataexchange.support.SqlDataExport;
import org.datagear.dataexchange.support.SqlDataImport;
import org.datagear.dbinfo.DatabaseInfoResolver;
import org.datagear.dbinfo.TableInfo;
import org.datagear.dbinfo.TableType;
import org.datagear.management.domain.Schema;
import org.datagear.management.service.SchemaService;
import org.datagear.util.FileInfo;
import org.datagear.util.FileUtil;
import org.datagear.util.IDUtil;
import org.datagear.util.IOUtil;
import org.datagear.web.OperationMessage;
import org.datagear.web.cometd.dataexchange.CometdBatchDataExchangeListener;
import org.datagear.web.cometd.dataexchange.CometdSubDataImportListener;
import org.datagear.web.cometd.dataexchange.CometdSubTextDataExportListener;
import org.datagear.web.cometd.dataexchange.CometdSubTextValueDataImportListener;
import org.datagear.web.cometd.dataexchange.DataExchangeCometdService;
import org.datagear.web.convert.ClassDataConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据交换控制器。
 * 
 * @author datagear@163.com
 *
 */
@Controller
@RequestMapping("/dataexchange")
public class DataExchangeController extends AbstractSchemaConnController
{
	public static final Pattern TABLE_NAME_QUERY_PATTERN = Pattern.compile("^\\s*\\S+\\s*$", Pattern.CASE_INSENSITIVE);

	protected static final String KEY_SESSION_BatchDataExchangeInfoMap = DataExchangeController.class.getName()
			+ ".BatchDataExchangeInfoMap";

	@Autowired
	private DataExchangeService<DataExchange> dataExchangeService;

	@Autowired
	@Qualifier("tempDataExchangeRootDirectory")
	private File tempDataExchangeRootDirectory;

	@Autowired
	private DataExchangeCometdService dataExchangeCometdService;

	@Autowired
	private DatabaseInfoResolver databaseInfoResolver;

	public DataExchangeController()
	{
		super();
	}

	public DataExchangeController(MessageSource messageSource, ClassDataConverter classDataConverter,
			SchemaService schemaService, ConnectionSource connectionSource,
			DataExchangeService<DataExchange> dataExchangeService, File tempDataExchangeRootDirectory,
			DataExchangeCometdService dataExchangeCometdService, DatabaseInfoResolver databaseInfoResolver)
	{
		super(messageSource, classDataConverter, schemaService, connectionSource);
		this.dataExchangeService = dataExchangeService;
		this.tempDataExchangeRootDirectory = tempDataExchangeRootDirectory;
		this.dataExchangeCometdService = dataExchangeCometdService;
		this.databaseInfoResolver = databaseInfoResolver;
	}

	public DataExchangeService<DataExchange> getDataExchangeService()
	{
		return dataExchangeService;
	}

	public void setDataExchangeService(DataExchangeService<DataExchange> dataExchangeService)
	{
		this.dataExchangeService = dataExchangeService;
	}

	public File getTempDataExchangeRootDirectory()
	{
		return tempDataExchangeRootDirectory;
	}

	public void setTempDataExchangeRootDirectory(File tempDataExchangeRootDirectory)
	{
		this.tempDataExchangeRootDirectory = tempDataExchangeRootDirectory;
	}

	public DataExchangeCometdService getDataExchangeCometdService()
	{
		return dataExchangeCometdService;
	}

	public void setDataExchangeCometdService(DataExchangeCometdService dataExchangeCometdService)
	{
		this.dataExchangeCometdService = dataExchangeCometdService;
	}

	public DatabaseInfoResolver getDatabaseInfoResolver()
	{
		return databaseInfoResolver;
	}

	public void setDatabaseInfoResolver(DatabaseInfoResolver databaseInfoResolver)
	{
		this.databaseInfoResolver = databaseInfoResolver;
	}

	@RequestMapping("/{schemaId}/import")
	public String impt(HttpServletRequest request, HttpServletResponse response,
			org.springframework.ui.Model springModel, @PathVariable("schemaId") String schemaId) throws Throwable
	{
		new VoidSchemaConnExecutor(request, response, springModel, schemaId, true)
		{
			@Override
			protected void execute(HttpServletRequest request, HttpServletResponse response, Model springModel,
					Schema schema) throws Throwable
			{
			}
		}.execute();

		return "/dataexchange/import";
	}

	@RequestMapping("/{schemaId}/import/csv")
	public String imptCsv(HttpServletRequest request, HttpServletResponse response,
			org.springframework.ui.Model springModel, @PathVariable("schemaId") String schemaId) throws Throwable
	{
		new VoidSchemaConnExecutor(request, response, springModel, schemaId, true)
		{
			@Override
			protected void execute(HttpServletRequest request, HttpServletResponse response, Model springModel,
					Schema schema) throws Throwable
			{
			}
		}.execute();

		DataFormat defaultDataFormat = new DataFormat();

		String dataExchangeId = IDUtil.uuid();

		springModel.addAttribute("defaultDataFormat", defaultDataFormat);
		springModel.addAttribute("dataExchangeId", dataExchangeId);
		springModel.addAttribute("dataExchangeChannelId", getDataExchangeChannelId(dataExchangeId));
		springModel.addAttribute("availableCharsetNames", getAvailableCharsetNames());
		springModel.addAttribute("defaultCharsetName", Charset.defaultCharset().name());

		return "/dataexchange/import_csv";
	}

	@RequestMapping(value = "/{schemaId}/import/csv/uploadImportFile", produces = CONTENT_TYPE_JSON)
	@ResponseBody
	public List<DataImportFileInfo> imptCsvUploadFile(HttpServletRequest request, HttpServletResponse response,
			@PathVariable("schemaId") String schemaId, @RequestParam("dataExchangeId") String dataExchangeId,
			@RequestParam("file") MultipartFile multipartFile) throws Exception
	{
		return uploadImportFile(request, response, schemaId, dataExchangeId, multipartFile, new CsvFileFilger());
	}

	@RequestMapping(value = "/{schemaId}/import/csv/doImport", produces = CONTENT_TYPE_JSON)
	@ResponseBody
	public ResponseEntity<OperationMessage> imptCsvDoImport(HttpServletRequest request, HttpServletResponse response,
			@PathVariable("schemaId") String schemaId, @RequestParam("dataExchangeId") String dataExchangeId,
			TextValueFileBatchDataImportForm dataImportForm,
			@RequestParam(value = "dependentNumberNone", required = false) String dependentNumberNone) throws Exception
	{
		if (dataImportForm == null || isEmpty(dataImportForm.getSubDataExchangeIds())
				|| isEmpty(dataImportForm.getFileNames()) || isEmpty(dataImportForm.getFileEncoding())
				|| isEmpty(dataImportForm.getNumbers()) || isEmpty(dataImportForm.getDependentNumbers())
				|| isEmpty(dataImportForm.getImportOption()) || isEmpty(dataImportForm.getDataFormat())
				|| isEmpty(dataImportForm.getTableNames())
				|| dataImportForm.getSubDataExchangeIds().length != dataImportForm.getFileNames().length
				|| dataImportForm.getSubDataExchangeIds().length != dataImportForm.getNumbers().length
				|| dataImportForm.getSubDataExchangeIds().length != dataImportForm.getDependentNumbers().length
				|| dataImportForm.getSubDataExchangeIds().length != dataImportForm.getTableNames().length)
			throw new IllegalInputException();

		String[] subDataExchangeIds = dataImportForm.getSubDataExchangeIds();
		String[] numbers = dataImportForm.getNumbers();
		String[] dependentNumbers = dataImportForm.getDependentNumbers();
		String[] fileNames = dataImportForm.getFileNames();
		String[] tableNames = dataImportForm.getTableNames();

		File directory = getTempDataExchangeDirectory(dataExchangeId, true);
		File logDirectory = getTempDataExchangeLogDirectory(dataExchangeId, true);

		Schema schema = getSchemaNotNull(request, response, schemaId);

		ConnectionFactory connectionFactory = new DataSourceConnectionFactory(new SchemaDataSource(schema));

		String importChannelId = getDataExchangeChannelId(dataExchangeId);
		ServerChannel importServerChannel = this.dataExchangeCometdService.getChannelWithCreation(importChannelId);

		Locale locale = getLocale(request);

		SubDataExchange[] subDataExchanges = new SubDataExchange[subDataExchangeIds.length];

		for (int i = 0; i < subDataExchangeIds.length; i++)
		{
			File file = FileUtil.getFile(directory, fileNames[i]);
			ResourceFactory<Reader> readerFactory = FileReaderResourceFactory.valueOf(file,
					dataImportForm.getFileEncoding());

			CsvDataImport csvDataImport = new CsvDataImport(connectionFactory, dataImportForm.getDataFormat(),
					dataImportForm.getImportOption(), tableNames[i], readerFactory);

			CometdSubTextValueDataImportListener listener = new CometdSubTextValueDataImportListener(
					this.dataExchangeCometdService, importServerChannel, getMessageSource(), locale,
					subDataExchangeIds[i], csvDataImport.getImportOption().getExceptionResolve());
			listener.setLogFile(getTempSubDataExchangeLogFile(logDirectory, subDataExchangeIds[i]));
			listener.setSendExchangingMessageInterval(
					evalSendDataExchangingMessageInterval(subDataExchangeIds.length, csvDataImport));
			csvDataImport.setListener(listener);

			SubDataExchange subDataExchange = new SubDataExchange(subDataExchangeIds[i], numbers[i], csvDataImport);
			subDataExchanges[i] = subDataExchange;
		}

		resolveSubDataExchangeDependencies(subDataExchanges, numbers, dependentNumbers, dependentNumberNone);

		Set<SubDataExchange> subDataExchangeSet = new HashSet<SubDataExchange>(subDataExchangeIds.length);
		Collections.addAll(subDataExchangeSet, subDataExchanges);

		BatchDataExchange batchDataExchange = buildBatchDataExchange(connectionFactory, subDataExchangeSet,
				importServerChannel, locale);

		this.dataExchangeService.exchange(batchDataExchange);

		BatchDataExchangeInfo batchDataExchangeInfo = new BatchDataExchangeInfo(dataExchangeId, batchDataExchange);
		storeBatchDataExchangeInfo(request, batchDataExchangeInfo);

		return buildOperationMessageSuccessEmptyResponseEntity();
	}

	@RequestMapping("/{schemaId}/import/sql")
	public String imptSql(HttpServletRequest request, HttpServletResponse response,
			org.springframework.ui.Model springModel, @PathVariable("schemaId") String schemaId) throws Throwable
	{
		new VoidSchemaConnExecutor(request, response, springModel, schemaId, true)
		{
			@Override
			protected void execute(HttpServletRequest request, HttpServletResponse response, Model springModel,
					Schema schema) throws Throwable
			{
			}
		}.execute();

		DataFormat defaultDataFormat = new DataFormat();

		String dataExchangeId = IDUtil.uuid();

		springModel.addAttribute("defaultDataFormat", defaultDataFormat);
		springModel.addAttribute("dataExchangeId", dataExchangeId);
		springModel.addAttribute("dataExchangeChannelId", getDataExchangeChannelId(dataExchangeId));
		springModel.addAttribute("availableCharsetNames", getAvailableCharsetNames());
		springModel.addAttribute("defaultCharsetName", Charset.defaultCharset().name());

		return "/dataexchange/import_sql";
	}

	@RequestMapping(value = "/{schemaId}/import/sql/uploadImportFile", produces = CONTENT_TYPE_JSON)
	@ResponseBody
	public List<DataImportFileInfo> imptSqlUploadFile(HttpServletRequest request, HttpServletResponse response,
			@PathVariable("schemaId") String schemaId, @RequestParam("dataExchangeId") String dataExchangeId,
			@RequestParam("file") MultipartFile multipartFile) throws Exception
	{
		return uploadImportFile(request, response, schemaId, dataExchangeId, multipartFile, new SqlFileFilger());
	}

	@RequestMapping(value = "/{schemaId}/import/sql/doImport", produces = CONTENT_TYPE_JSON)
	@ResponseBody
	public ResponseEntity<OperationMessage> imptSqlDoImport(HttpServletRequest request, HttpServletResponse response,
			@PathVariable("schemaId") String schemaId, @RequestParam("dataExchangeId") String dataExchangeId,
			SqlFileBatchDataImportForm dataImportForm,
			@RequestParam(value = "dependentNumberNone", required = false) String dependentNumberNone) throws Exception
	{
		if (dataImportForm == null || isEmpty(dataImportForm.getSubDataExchangeIds())
				|| isEmpty(dataImportForm.getFileNames()) || isEmpty(dataImportForm.getFileEncoding())
				|| isEmpty(dataImportForm.getNumbers()) || isEmpty(dataImportForm.getDependentNumbers())
				|| isEmpty(dataImportForm.getImportOption())
				|| dataImportForm.getSubDataExchangeIds().length != dataImportForm.getFileNames().length
				|| dataImportForm.getSubDataExchangeIds().length != dataImportForm.getNumbers().length
				|| dataImportForm.getSubDataExchangeIds().length != dataImportForm.getDependentNumbers().length)
			throw new IllegalInputException();

		String[] subDataExchangeIds = dataImportForm.getSubDataExchangeIds();
		String[] numbers = dataImportForm.getNumbers();
		String[] dependentNumbers = dataImportForm.getDependentNumbers();
		String[] fileNames = dataImportForm.getFileNames();

		File directory = getTempDataExchangeDirectory(dataExchangeId, true);
		File logDirectory = getTempDataExchangeLogDirectory(dataExchangeId, true);

		Schema schema = getSchemaNotNull(request, response, schemaId);

		ConnectionFactory connectionFactory = new DataSourceConnectionFactory(new SchemaDataSource(schema));

		String importChannelId = getDataExchangeChannelId(dataExchangeId);
		ServerChannel importServerChannel = this.dataExchangeCometdService.getChannelWithCreation(importChannelId);

		Locale locale = getLocale(request);

		SubDataExchange[] subDataExchanges = new SubDataExchange[subDataExchangeIds.length];

		for (int i = 0; i < subDataExchangeIds.length; i++)
		{
			File file = FileUtil.getFile(directory, fileNames[i]);
			ResourceFactory<Reader> readerFactory = FileReaderResourceFactory.valueOf(file,
					dataImportForm.getFileEncoding());

			SqlDataImport sqlDataImport = new SqlDataImport(connectionFactory, dataImportForm.getImportOption(),
					readerFactory);

			CometdSubDataImportListener listener = new CometdSubDataImportListener(this.dataExchangeCometdService,
					importServerChannel, getMessageSource(), locale, subDataExchangeIds[i],
					dataImportForm.getImportOption().getExceptionResolve());
			listener.setLogFile(getTempSubDataExchangeLogFile(logDirectory, subDataExchangeIds[i]));
			listener.setSendExchangingMessageInterval(
					evalSendDataExchangingMessageInterval(subDataExchangeIds.length, sqlDataImport));
			sqlDataImport.setListener(listener);

			SubDataExchange subDataExchange = new SubDataExchange(subDataExchangeIds[i], numbers[i], sqlDataImport);
			subDataExchanges[i] = subDataExchange;
		}

		resolveSubDataExchangeDependencies(subDataExchanges, numbers, dependentNumbers, dependentNumberNone);

		Set<SubDataExchange> subDataExchangeSet = new HashSet<SubDataExchange>(subDataExchangeIds.length);
		Collections.addAll(subDataExchangeSet, subDataExchanges);

		BatchDataExchange batchDataExchange = buildBatchDataExchange(connectionFactory, subDataExchangeSet,
				importServerChannel, locale);

		this.dataExchangeService.exchange(batchDataExchange);

		BatchDataExchangeInfo batchDataExchangeInfo = new BatchDataExchangeInfo(dataExchangeId, batchDataExchange);
		storeBatchDataExchangeInfo(request, batchDataExchangeInfo);

		return buildOperationMessageSuccessEmptyResponseEntity();
	}

	@RequestMapping("/{schemaId}/import/db")
	public String imptDb(HttpServletRequest request, HttpServletResponse response,
			org.springframework.ui.Model springModel, @PathVariable("schemaId") String schemaId) throws Throwable
	{
		new VoidSchemaConnExecutor(request, response, springModel, schemaId, true)
		{
			@Override
			protected void execute(HttpServletRequest request, HttpServletResponse response, Model springModel,
					Schema schema) throws Throwable
			{
			}
		}.execute();

		springModel.addAttribute("dataExchangeId", IDUtil.uuid());

		return "/dataexchange/import_db";
	}

	/**
	 * 查看子数据交换日志。
	 * 
	 * @param request
	 * @param response
	 * @param schemaId
	 * @param dataExchangeId
	 * @param subDataExchangeId
	 * @throws Throwable
	 */
	@RequestMapping(value = "/{schemaId}/viewLog")
	public String viewLog(HttpServletRequest request, HttpServletResponse response,
			org.springframework.ui.Model springModel, @PathVariable("schemaId") String schemaId,
			@RequestParam("dataExchangeId") String dataExchangeId,
			@RequestParam("subDataExchangeId") String subDataExchangeId) throws Throwable
	{
		new VoidSchemaConnExecutor(request, response, springModel, schemaId, true)
		{
			@Override
			protected void execute(HttpServletRequest request, HttpServletResponse response, Model springModel,
					Schema schema) throws Throwable
			{
			}
		}.execute();

		springModel.addAttribute("dataExchangeId", dataExchangeId);
		springModel.addAttribute("subDataExchangeId", subDataExchangeId);

		return "/dataexchange/view_log";
	}

	/**
	 * 查看子数据交换日志。
	 * 
	 * @param request
	 * @param response
	 * @param schemaId
	 * @param dataExchangeId
	 * @param subDataExchangeId
	 * @throws Throwable
	 */
	@RequestMapping(value = "/{schemaId}/getLogContent")
	public void getLogContent(HttpServletRequest request, HttpServletResponse response,
			@PathVariable("schemaId") String schemaId, @RequestParam("dataExchangeId") String dataExchangeId,
			@RequestParam("subDataExchangeId") String subDataExchangeId) throws Throwable
	{
		File logDirectory = getTempDataExchangeLogDirectory(dataExchangeId, true);
		File logFile = getTempSubDataExchangeLogFile(logDirectory, subDataExchangeId);

		if (!logFile.exists())
			throw new IllegalInputException();

		response.setCharacterEncoding(RESPONSE_ENCODING);
		response.setContentType(CONTENT_TYPE_HTML);

		PrintWriter out = response.getWriter();

		Reader reader = null;
		try
		{
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile),
					CometdSubTextValueDataImportListener.LOG_FILE_CHARSET));

			IOUtil.write(reader, out);
		}
		finally
		{
			IOUtil.close(reader);
			out.flush();
		}
	}

	@RequestMapping("/{schemaId}/export")
	public String expt(HttpServletRequest request, HttpServletResponse response,
			org.springframework.ui.Model springModel, @PathVariable("schemaId") String schemaId) throws Throwable
	{
		new VoidSchemaConnExecutor(request, response, springModel, schemaId, true)
		{
			@Override
			protected void execute(HttpServletRequest request, HttpServletResponse response, Model springModel,
					Schema schema) throws Throwable
			{
			}
		}.execute();

		return "/dataexchange/export";
	}

	@RequestMapping("/{schemaId}/export/csv")
	public String exptCsv(HttpServletRequest request, HttpServletResponse response,
			org.springframework.ui.Model springModel, @PathVariable("schemaId") String schemaId) throws Throwable
	{
		new VoidSchemaConnExecutor(request, response, springModel, schemaId, true)
		{
			@Override
			protected void execute(HttpServletRequest request, HttpServletResponse response, Model springModel,
					Schema schema) throws Throwable
			{
			}
		}.execute();

		DataFormat defaultDataFormat = new DataFormat();

		String dataExchangeId = IDUtil.uuid();

		springModel.addAttribute("defaultDataFormat", defaultDataFormat);
		springModel.addAttribute("dataExchangeId", dataExchangeId);
		springModel.addAttribute("dataExchangeChannelId", getDataExchangeChannelId(dataExchangeId));
		springModel.addAttribute("availableCharsetNames", getAvailableCharsetNames());
		springModel.addAttribute("defaultCharsetName", Charset.defaultCharset().name());

		return "/dataexchange/export_csv";
	}

	@RequestMapping(value = "/{schemaId}/export/csv/doExport", produces = CONTENT_TYPE_JSON)
	@ResponseBody
	public ResponseEntity<OperationMessage> exptCsvDoExport(HttpServletRequest request, HttpServletResponse response,
			@PathVariable("schemaId") String schemaId, @RequestParam("dataExchangeId") String dataExchangeId,
			TextFileBatchDataExportForm exportForm) throws Exception
	{
		if (exportForm == null || isEmpty(exportForm.getDataFormat()) || isEmpty(exportForm.getExportOption())
				|| isEmpty(exportForm.getFileEncoding()) || isEmpty(exportForm.getSubDataExchangeIds())
				|| isEmpty(exportForm.getQueries()) || isEmpty(exportForm.getFileNames())
				|| exportForm.getSubDataExchangeIds().length != exportForm.getQueries().length
				|| exportForm.getSubDataExchangeIds().length != exportForm.getFileNames().length)
			throw new IllegalInputException();

		String[] subDataExchangeIds = exportForm.getSubDataExchangeIds();
		String[] queries = exportForm.getQueries();
		String[] fileNames = exportForm.getFileNames();

		File directory = getTempDataExchangeDirectory(dataExchangeId, true);
		File logDirectory = getTempDataExchangeLogDirectory(dataExchangeId, true);

		Schema schema = getSchemaNotNull(request, response, schemaId);
		ConnectionFactory connectionFactory = new DataSourceConnectionFactory(new SchemaDataSource(schema));

		String exportChannelId = getDataExchangeChannelId(dataExchangeId);
		ServerChannel exportServerChannel = this.dataExchangeCometdService.getChannelWithCreation(exportChannelId);

		Locale locale = getLocale(request);

		Set<SubDataExchange> subDataExchanges = new HashSet<SubDataExchange>();

		for (int i = 0; i < subDataExchangeIds.length; i++)
		{
			Query query = toQuery(queries[i]);

			File file = FileUtil.getFile(directory, fileNames[i]);
			ResourceFactory<Writer> writerFactory = FileWriterResourceFactory.valueOf(file,
					exportForm.getFileEncoding());

			CsvDataExport csvDataExport = new CsvDataExport(connectionFactory, exportForm.getDataFormat(),
					exportForm.getExportOption(), query, writerFactory);

			CometdSubTextDataExportListener listener = new CometdSubTextDataExportListener(
					this.dataExchangeCometdService, exportServerChannel, getMessageSource(), getLocale(request),
					subDataExchangeIds[i]);
			listener.setLogFile(getTempSubDataExchangeLogFile(logDirectory, subDataExchangeIds[i]));
			listener.setSendExchangingMessageInterval(
					evalSendDataExchangingMessageInterval(subDataExchangeIds.length, csvDataExport));
			csvDataExport.setListener(listener);

			SubDataExchange subDataExchange = new SubDataExchange(subDataExchangeIds[i], csvDataExport);
			subDataExchanges.add(subDataExchange);
		}

		BatchDataExchange batchDataExchange = buildBatchDataExchange(connectionFactory, subDataExchanges,
				exportServerChannel, locale);

		this.dataExchangeService.exchange(batchDataExchange);

		BatchDataExchangeInfo batchDataExchangeInfo = new BatchDataExchangeInfo(dataExchangeId, batchDataExchange);
		storeBatchDataExchangeInfo(request, batchDataExchangeInfo);

		ResponseEntity<OperationMessage> responseEntity = buildOperationMessageSuccessEmptyResponseEntity();

		Map<String, String> subDataExchangeFileNameMap = buildSubDataExchangeFileNameMap(subDataExchangeIds, fileNames);
		responseEntity.getBody().setData(subDataExchangeFileNameMap);

		return responseEntity;
	}

	@RequestMapping("/{schemaId}/export/sql")
	public String exptSql(HttpServletRequest request, HttpServletResponse response,
			org.springframework.ui.Model springModel, @PathVariable("schemaId") String schemaId) throws Throwable
	{
		new VoidSchemaConnExecutor(request, response, springModel, schemaId, true)
		{
			@Override
			protected void execute(HttpServletRequest request, HttpServletResponse response, Model springModel,
					Schema schema) throws Throwable
			{
			}
		}.execute();

		DataFormat defaultDataFormat = new DataFormat();

		String dataExchangeId = IDUtil.uuid();

		springModel.addAttribute("defaultDataFormat", defaultDataFormat);
		springModel.addAttribute("dataExchangeId", dataExchangeId);
		springModel.addAttribute("dataExchangeChannelId", getDataExchangeChannelId(dataExchangeId));
		springModel.addAttribute("availableCharsetNames", getAvailableCharsetNames());
		springModel.addAttribute("defaultCharsetName", Charset.defaultCharset().name());

		return "/dataexchange/export_sql";
	}

	@RequestMapping(value = "/{schemaId}/export/sql/doExport", produces = CONTENT_TYPE_JSON)
	@ResponseBody
	public ResponseEntity<OperationMessage> exptSqlDoExport(HttpServletRequest request, HttpServletResponse response,
			@PathVariable("schemaId") String schemaId, @RequestParam("dataExchangeId") String dataExchangeId,
			SqlFileBatchDataExportForm exportForm) throws Exception
	{
		if (exportForm == null || isEmpty(exportForm.getDataFormat()) || isEmpty(exportForm.getExportOption())
				|| isEmpty(exportForm.getFileEncoding()) || isEmpty(exportForm.getSubDataExchangeIds())
				|| isEmpty(exportForm.getQueries()) || isEmpty(exportForm.getTableNames())
				|| isEmpty(exportForm.getFileNames())
				|| exportForm.getSubDataExchangeIds().length != exportForm.getTableNames().length
				|| exportForm.getSubDataExchangeIds().length != exportForm.getQueries().length
				|| exportForm.getSubDataExchangeIds().length != exportForm.getFileNames().length)
			throw new IllegalInputException();

		String[] subDataExchangeIds = exportForm.getSubDataExchangeIds();
		String[] queries = exportForm.getQueries();
		String[] tableNames = exportForm.getTableNames();
		String[] fileNames = exportForm.getFileNames();

		File directory = getTempDataExchangeDirectory(dataExchangeId, true);
		File logDirectory = getTempDataExchangeLogDirectory(dataExchangeId, true);

		Schema schema = getSchemaNotNull(request, response, schemaId);
		ConnectionFactory connectionFactory = new DataSourceConnectionFactory(new SchemaDataSource(schema));

		String exportChannelId = getDataExchangeChannelId(dataExchangeId);
		ServerChannel exportServerChannel = this.dataExchangeCometdService.getChannelWithCreation(exportChannelId);

		Locale locale = getLocale(request);

		Set<SubDataExchange> subDataExchanges = new HashSet<SubDataExchange>();

		for (int i = 0; i < subDataExchangeIds.length; i++)
		{
			Query query = toQuery(queries[i]);

			File file = FileUtil.getFile(directory, fileNames[i]);
			ResourceFactory<Writer> writerFactory = FileWriterResourceFactory.valueOf(file,
					exportForm.getFileEncoding());

			SqlDataExport sqlDataExport = new SqlDataExport(connectionFactory, exportForm.getDataFormat(),
					exportForm.getExportOption(), query, tableNames[i], writerFactory);

			CometdSubTextDataExportListener listener = new CometdSubTextDataExportListener(
					this.dataExchangeCometdService, exportServerChannel, getMessageSource(), getLocale(request),
					subDataExchangeIds[i]);
			listener.setLogFile(getTempSubDataExchangeLogFile(logDirectory, subDataExchangeIds[i]));
			listener.setSendExchangingMessageInterval(
					evalSendDataExchangingMessageInterval(subDataExchangeIds.length, sqlDataExport));
			sqlDataExport.setListener(listener);

			SubDataExchange subDataExchange = new SubDataExchange(subDataExchangeIds[i], sqlDataExport);
			subDataExchanges.add(subDataExchange);
		}

		BatchDataExchange batchDataExchange = buildBatchDataExchange(connectionFactory, subDataExchanges,
				exportServerChannel, locale);

		this.dataExchangeService.exchange(batchDataExchange);

		BatchDataExchangeInfo batchDataExchangeInfo = new BatchDataExchangeInfo(dataExchangeId, batchDataExchange);
		storeBatchDataExchangeInfo(request, batchDataExchangeInfo);

		ResponseEntity<OperationMessage> responseEntity = buildOperationMessageSuccessEmptyResponseEntity();

		Map<String, String> subDataExchangeFileNameMap = buildSubDataExchangeFileNameMap(subDataExchangeIds, fileNames);
		responseEntity.getBody().setData(subDataExchangeFileNameMap);

		return responseEntity;
	}

	@RequestMapping(value = "/{schemaId}/export/download")
	@ResponseBody
	public void exptDownload(HttpServletRequest request, HttpServletResponse response,
			@PathVariable("schemaId") String schemaId, @RequestParam("dataExchangeId") String dataExchangeId,
			@RequestParam("fileName") String fileName) throws Exception
	{
		response.setCharacterEncoding(RESPONSE_ENCODING);
		response.setHeader("Content-Disposition",
				"attachment; filename=" + new String(fileName.getBytes(RESPONSE_ENCODING), "iso-8859-1") + "");

		File directory = getTempDataExchangeDirectory(dataExchangeId, true);
		File file = FileUtil.getFile(directory, fileName);

		OutputStream out = null;

		try
		{
			out = response.getOutputStream();
			IOUtil.write(file, out);
		}
		finally
		{
			IOUtil.close(out);
		}
	}

	@RequestMapping(value = "/{schemaId}/export/downloadAll")
	@ResponseBody
	public void exptDownloadAll(HttpServletRequest request, HttpServletResponse response,
			@PathVariable("schemaId") String schemaId, @RequestParam("dataExchangeId") String dataExchangeId)
			throws Exception
	{
		String fileName = "export.zip";

		response.setCharacterEncoding(RESPONSE_ENCODING);
		response.setHeader("Content-Disposition",
				"attachment; filename=" + new String(fileName.getBytes(RESPONSE_ENCODING), "iso-8859-1") + "");

		File directory = getTempDataExchangeDirectory(dataExchangeId, true);

		ZipOutputStream out = null;
		try
		{
			out = new ZipOutputStream(response.getOutputStream());
			IOUtil.writeFileToZipOutputStream(out, directory, null);
		}
		finally
		{
			IOUtil.close(out);
		}
	}

	/**
	 * 处理{@linkplain SubDataExchange}依赖。
	 * 
	 * @param subDataExchanges
	 * @param numbers
	 * @param dependentNumbers
	 * @param dependentNumberNone
	 */
	protected void resolveSubDataExchangeDependencies(SubDataExchange[] subDataExchanges, String[] numbers,
			String[] dependentNumbers, String dependentNumberNone)
	{
		for (int i = 0; i < subDataExchanges.length; i++)
		{
			SubDataExchange subDataExchange = subDataExchanges[i];
			String dependentNumber = dependentNumbers[i];

			if (dependentNumber == null)
				continue;

			dependentNumber = dependentNumber.trim();

			if (isEmpty(dependentNumber) || dependentNumber.equals(dependentNumberNone))
				continue;

			String[] myDependentNumbers = dependentNumber.split(",");

			Set<SubDataExchange> myDependencies = new HashSet<SubDataExchange>();

			for (int j = 0; j < myDependentNumbers.length; j++)
			{
				String myDependentNumber = myDependentNumbers[j].trim();

				if (isEmpty(myDependentNumber))
					continue;

				for (int k = 0; k < numbers.length; k++)
				{
					if (numbers[k].equals(myDependentNumber))
					{
						myDependencies.add(subDataExchanges[k]);
						break;
					}
				}
			}

			if (!myDependencies.isEmpty())
				subDataExchange.setDependencies(myDependencies);
		}
	}

	protected List<DataImportFileInfo> uploadImportFile(HttpServletRequest request, HttpServletResponse response,
			String schemaId, String dataExchangeId, MultipartFile multipartFile, FileFilter fileFilter) throws Exception
	{
		List<DataImportFileInfo> fileInfos = new ArrayList<DataImportFileInfo>();

		File directory = getTempDataExchangeDirectory(dataExchangeId, true);

		String rawFileName = multipartFile.getOriginalFilename();
		boolean isZipFile = isZipFile(rawFileName);
		String serverFileName = IDUtil.uuid();

		if (isZipFile)
		{
			File unzipDirectory = FileUtil.getFile(directory, serverFileName);
			FileUtil.deleteFile(unzipDirectory);
			unzipDirectory.mkdirs();

			ZipInputStream in = null;

			try
			{
				in = new ZipInputStream(multipartFile.getInputStream());
				// TODO ZIP中存在中文名文件时解压会报错
				IOUtil.unzip(in, unzipDirectory);
			}
			finally
			{
				IOUtil.close(in);
			}

			listDataImportFileInfos(unzipDirectory, fileFilter, serverFileName, rawFileName, fileInfos);
		}
		else
		{
			File importFile = FileUtil.getFile(directory, serverFileName);

			InputStream in = null;
			OutputStream importFileOut = null;
			try
			{
				in = multipartFile.getInputStream();
				importFileOut = IOUtil.getOutputStream(importFile);
				IOUtil.write(in, importFileOut);
			}
			finally
			{
				IOUtil.close(in);
				IOUtil.close(importFileOut);
			}

			DataImportFileInfo fileInfo = new DataImportFileInfo(serverFileName, importFile.length(), rawFileName,
					DataImportFileInfo.fileNameToTableName(rawFileName));

			fileInfos.add(fileInfo);
		}

		return fileInfos;
	}

	protected BatchDataExchange buildBatchDataExchange(ConnectionFactory connectionFactory,
			Set<SubDataExchange> subDataExchanges, ServerChannel serverChannel, Locale locale)
	{
		BatchDataExchange batchDataExchange = new SimpleBatchDataExchange(connectionFactory, subDataExchanges);

		CometdBatchDataExchangeListener listener = new CometdBatchDataExchangeListener(this.dataExchangeCometdService,
				serverChannel, getMessageSource(), locale);
		batchDataExchange.setListener(listener);

		return batchDataExchange;
	}

	protected Map<String, String> buildSubDataExchangeFileNameMap(String[] subDataExchangeIds, String[] fileNames)
	{
		Map<String, String> map = new HashMap<String, String>();

		for (int i = 0; i < subDataExchangeIds.length; i++)
			map.put(subDataExchangeIds[i], fileNames[i]);

		return map;
	}

	protected Query toQuery(String query)
	{
		if (isTableNameQueryString(query))
			return new TableQuery(query);
		else
			return new SqlQuery(query);
	}

	protected boolean isTableNameQueryString(String query)
	{
		return TABLE_NAME_QUERY_PATTERN.matcher(query).matches();
	}

	@RequestMapping(value = "/{schemaId}/getAllTableNames", produces = CONTENT_TYPE_JSON)
	@ResponseBody
	public List<String> getAllTableNames(HttpServletRequest request, HttpServletResponse response,
			org.springframework.ui.Model springModel, @PathVariable("schemaId") String schemaId) throws Throwable
	{
		TableInfo[] tableInfos = new ReturnSchemaConnExecutor<TableInfo[]>(request, response, springModel, schemaId,
				true)
		{
			@Override
			protected TableInfo[] execute(HttpServletRequest request, HttpServletResponse response,
					org.springframework.ui.Model springModel, Schema schema) throws Throwable
			{
				return getDatabaseInfoResolver().getTableInfos(getConnection());
			}

		}.execute();

		List<String> tableNames = excludeViewNames(tableInfos);
		Collections.sort(tableNames);

		return tableNames;
	}

	@RequestMapping(value = "/{schemaId}/cancel")
	@ResponseBody
	public ResponseEntity<OperationMessage> cancel(HttpServletRequest request, HttpServletResponse response,
			@PathVariable("schemaId") String schemaId, @RequestParam("dataExchangeId") String dataExchangeId)
			throws Exception
	{
		String[] subDataExchangeIds = request.getParameterValues("subDataExchangeId");

		if (isEmpty(subDataExchangeIds))
			throw new IllegalInputException();

		BatchDataExchangeInfo batchDataExchangeInfo = retrieveBatchDataExchangeInfo(request, dataExchangeId);

		if (batchDataExchangeInfo == null)
			throw new IllegalInputException();

		BatchDataExchangeContext batchDataExchangeContext = batchDataExchangeInfo.getBatchDataExchange().getContext();

		for (int i = 0; i < subDataExchangeIds.length; i++)
			batchDataExchangeContext.cancel(subDataExchangeIds[i]);

		ResponseEntity<OperationMessage> responseEntity = buildOperationMessageSuccessEmptyResponseEntity();

		return responseEntity;
	}

	protected List<String> excludeViewNames(TableInfo[] tableInfos)
	{
		List<String> list = new ArrayList<String>(tableInfos.length);

		for (TableInfo tableInfo : tableInfos)
		{
			if (TableType.TABLE.equals(tableInfo.getType()))
				list.add(tableInfo.getName());
		}

		return list;
	}

	/**
	 * 计算导入/导出中消息发送间隔。
	 * <p>
	 * 如果发送频率过快，当数据交换很多时会出现cometd卡死的情况。
	 * </p>
	 * 
	 * @param total
	 * @param dataExchange
	 * @return
	 */
	protected int evalSendDataExchangingMessageInterval(int total, DataExchange dataExchange)
	{
		int interval = 500 * total;

		if (interval > 5000)
			interval = 5000;

		return interval;
	}

	/**
	 * 将{@linkplain BatchDataExchangeInfo}存储至session中。
	 * 
	 * @param request
	 * @param batchDataExchangeInfo
	 */
	@SuppressWarnings("unchecked")
	protected void storeBatchDataExchangeInfo(HttpServletRequest request, BatchDataExchangeInfo batchDataExchangeInfo)
	{
		HttpSession session = request.getSession();

		Hashtable<String, BatchDataExchangeInfo> map = null;

		synchronized (session)
		{
			map = (Hashtable<String, BatchDataExchangeInfo>) session.getAttribute(KEY_SESSION_BatchDataExchangeInfoMap);

			if (map == null)
			{
				map = new Hashtable<String, BatchDataExchangeInfo>();
				session.setAttribute(KEY_SESSION_BatchDataExchangeInfoMap, map);
			}
		}

		map.put(batchDataExchangeInfo.getDataExchangeId(), batchDataExchangeInfo);
	}

	/**
	 * 从session中取回{@linkplain BatchDataExchangeInfo}。
	 * 
	 * @param request
	 * @param dataExchangeId
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected BatchDataExchangeInfo retrieveBatchDataExchangeInfo(HttpServletRequest request, String dataExchangeId)
	{
		HttpSession session = request.getSession();

		Hashtable<String, BatchDataExchangeInfo> map = (Hashtable<String, BatchDataExchangeInfo>) session
				.getAttribute(KEY_SESSION_BatchDataExchangeInfoMap);

		if (map == null)
			return null;

		return map.get(dataExchangeId);
	}

	protected void listDataImportFileInfos(File directory, FileFilter fileFilter, String parentPath,
			String displayParentPath, List<DataImportFileInfo> dataImportFileInfos)
	{
		if (parentPath == null)
			parentPath = "";
		else if (parentPath.isEmpty())
			;
		else
		{
			if (!parentPath.endsWith(File.separator))
				parentPath += File.separator;
		}

		if (displayParentPath == null)
			displayParentPath = "";
		else if (displayParentPath.isEmpty())
			;
		else
		{
			if (!displayParentPath.endsWith(File.separator))
				displayParentPath += File.separator;
		}

		File[] files = directory.listFiles(fileFilter);

		for (File file : files)
		{
			String myPath = parentPath + file.getName();
			String myDisplayPath = displayParentPath + file.getName();

			if (file.isDirectory())
				listDataImportFileInfos(file, fileFilter, myPath, myDisplayPath, dataImportFileInfos);
			else
			{
				DataImportFileInfo fileInfo = new DataImportFileInfo(myPath, file.length(), myDisplayPath,
						DataImportFileInfo.fileNameToTableName(file.getName()));

				dataImportFileInfos.add(fileInfo);
			}
		}
	}

	protected boolean isZipFile(String fileName)
	{
		if (fileName == null || fileName.isEmpty())
			return false;

		return fileName.toLowerCase().endsWith(".zip");
	}

	protected File getTempDataExchangeDirectory(String dataExchangeId, boolean notNull)
	{
		File directory = FileUtil.getFile(this.tempDataExchangeRootDirectory, dataExchangeId);

		if (notNull && !directory.exists())
			directory.mkdirs();

		return directory;
	}

	protected File getTempDataExchangeLogDirectory(String dataExchangeId, boolean notNull)
	{
		File directory = FileUtil.getFile(this.tempDataExchangeRootDirectory, dataExchangeId + "_logs");

		if (notNull && !directory.exists())
			directory.mkdirs();

		return directory;
	}

	protected File getTempSubDataExchangeLogFile(File logDirectory, String subDataExchangeId)
	{
		File logFile = FileUtil.getFile(logDirectory, "log_" + subDataExchangeId + ".txt");
		return logFile;
	}

	protected File getExportFileZip(String dataExchangeId)
	{
		File file = FileUtil.getFile(this.tempDataExchangeRootDirectory, dataExchangeId + ".zip");
		return file;
	}

	/**
	 * 获取指定数据交换操作ID对应的cometd通道ID。
	 * 
	 * @param dataExchangeId
	 * @return
	 */
	protected String getDataExchangeChannelId(String dataExchangeId)
	{
		return "/dataexchange/channel/" + dataExchangeId;
	}

	/**
	 * 获取可用字符集名称。
	 * 
	 * @return
	 */
	protected List<String> getAvailableCharsetNames()
	{
		List<String> charsetNames = new ArrayList<String>();

		Map<String, Charset> map = Charset.availableCharsets();
		Set<String> names = map.keySet();
		for (String name : names)
		{
			// 排除未在IANA注册的字符集
			if (name.startsWith("x-") || name.startsWith("X-"))
				continue;

			charsetNames.add(name);
		}

		return charsetNames;
	}

	public static class DataImportFileInfo extends FileInfo
	{
		private static final long serialVersionUID = 1L;

		/** 导入表名 */
		private String tableName;

		public DataImportFileInfo()
		{
			super();
		}

		public DataImportFileInfo(String name, long bytes, String displayName, String tableName)
		{
			super(name, bytes);
			super.setDisplayName(displayName);
			this.tableName = tableName;
		}

		public String getTableName()
		{
			return tableName;
		}

		public void setTableName(String tableName)
		{
			this.tableName = tableName;
		}

		/**
		 * 文件名转换为表名。
		 * 
		 * @param fileName
		 * @return
		 */
		public static String fileNameToTableName(String fileName)
		{
			if (fileName == null || fileName.isEmpty())
				return "";

			int slashIndex = fileName.indexOf('/');
			if (slashIndex < 0)
				slashIndex = fileName.indexOf('\\');

			String tableName = fileName.substring(slashIndex + 1);

			int dotIndex = tableName.indexOf('.');

			tableName = tableName.substring(0, dotIndex);

			return tableName;
		}
	}

	protected static class CsvFileFilger implements FileFilter
	{
		public CsvFileFilger()
		{
			super();
		}

		@Override
		public boolean accept(File file)
		{
			if (file.isDirectory())
				return true;
			else
				return file.getName().toLowerCase().endsWith(".csv");
		}
	}

	protected static class SqlFileFilger implements FileFilter
	{
		public SqlFileFilger()
		{
			super();
		}

		@Override
		public boolean accept(File file)
		{
			if (file.isDirectory())
				return true;
			else
				return file.getName().toLowerCase().endsWith(".sql");
		}
	}

	public static class AbstractFileBatchDataExchangeForm implements Serializable
	{
		private static final long serialVersionUID = 1L;

		private String[] subDataExchangeIds;

		private String[] fileNames;

		private String fileEncoding;

		public AbstractFileBatchDataExchangeForm()
		{
			super();
		}

		public String[] getSubDataExchangeIds()
		{
			return subDataExchangeIds;
		}

		public void setSubDataExchangeIds(String[] subDataExchangeIds)
		{
			this.subDataExchangeIds = subDataExchangeIds;
		}

		public String[] getFileNames()
		{
			return fileNames;
		}

		public void setFileNames(String[] fileNames)
		{
			this.fileNames = fileNames;
		}

		public String getFileEncoding()
		{
			return fileEncoding;
		}

		public void setFileEncoding(String fileEncoding)
		{
			this.fileEncoding = fileEncoding;
		}
	}

	public static class AbstractFileBatchDataImportForm extends AbstractFileBatchDataExchangeForm
			implements Serializable
	{
		private static final long serialVersionUID = 1L;

		/** 导入条目编号 */
		private String[] numbers;

		/** 导入条目依赖编号 */
		private String[] dependentNumbers;

		public AbstractFileBatchDataImportForm()
		{
			super();
		}

		public String[] getNumbers()
		{
			return numbers;
		}

		public void setNumbers(String[] numbers)
		{
			this.numbers = numbers;
		}

		public String[] getDependentNumbers()
		{
			return dependentNumbers;
		}

		public void setDependentNumbers(String[] dependentNumbers)
		{
			this.dependentNumbers = dependentNumbers;
		}
	}

	public static class TextValueFileBatchDataImportForm extends AbstractFileBatchDataImportForm implements Serializable
	{
		private static final long serialVersionUID = 1L;

		private TextValueDataImportOption importOption;

		private DataFormat dataFormat;

		private String[] tableNames;

		public TextValueFileBatchDataImportForm()
		{
			super();
		}

		public TextValueDataImportOption getImportOption()
		{
			return importOption;
		}

		public void setImportOption(TextValueDataImportOption importOption)
		{
			this.importOption = importOption;
		}

		public DataFormat getDataFormat()
		{
			return dataFormat;
		}

		public void setDataFormat(DataFormat dataFormat)
		{
			this.dataFormat = dataFormat;
		}

		public String[] getTableNames()
		{
			return tableNames;
		}

		public void setTableNames(String[] tableNames)
		{
			this.tableNames = tableNames;
		}
	}

	public static class SqlFileBatchDataImportForm extends AbstractFileBatchDataImportForm implements Serializable
	{
		private static final long serialVersionUID = 1L;

		private DataImportOption importOption;

		public SqlFileBatchDataImportForm()
		{
			super();
		}

		public DataImportOption getImportOption()
		{
			return importOption;
		}

		public void setImportOption(DataImportOption importOption)
		{
			this.importOption = importOption;
		}
	}

	public static class TextFileBatchDataExportForm extends AbstractFileBatchDataExchangeForm implements Serializable
	{
		private static final long serialVersionUID = 1L;

		private TextDataExportOption exportOption;

		private DataFormat dataFormat;

		private String[] queries;

		public TextFileBatchDataExportForm()
		{
			super();
		}

		public TextDataExportOption getExportOption()
		{
			return exportOption;
		}

		public void setExportOption(TextDataExportOption exportOption)
		{
			this.exportOption = exportOption;
		}

		public DataFormat getDataFormat()
		{
			return dataFormat;
		}

		public void setDataFormat(DataFormat dataFormat)
		{
			this.dataFormat = dataFormat;
		}

		public String[] getQueries()
		{
			return queries;
		}

		public void setQueries(String[] queries)
		{
			this.queries = queries;
		}
	}

	public static class SqlFileBatchDataExportForm extends TextFileBatchDataExportForm
	{
		private static final long serialVersionUID = 1L;

		private String[] tableNames;

		public SqlFileBatchDataExportForm()
		{
			super();
		}

		public String[] getTableNames()
		{
			return tableNames;
		}

		public void setTableNames(String[] tableNames)
		{
			this.tableNames = tableNames;
		}
	}

	protected static class BatchDataExchangeInfo implements Serializable
	{
		private static final long serialVersionUID = 1L;

		private String dataExchangeId;

		private transient BatchDataExchange batchDataExchange;

		public BatchDataExchangeInfo()
		{
			super();
		}

		public BatchDataExchangeInfo(String dataExchangeId, BatchDataExchange batchDataExchange)
		{
			super();
			this.dataExchangeId = dataExchangeId;
			this.batchDataExchange = batchDataExchange;
		}

		public String getDataExchangeId()
		{
			return dataExchangeId;
		}

		public void setDataExchangeId(String dataExchangeId)
		{
			this.dataExchangeId = dataExchangeId;
		}

		public BatchDataExchange getBatchDataExchange()
		{
			return batchDataExchange;
		}

		public void setBatchDataExchange(BatchDataExchange batchDataExchange)
		{
			this.batchDataExchange = batchDataExchange;
		}
	}
}
