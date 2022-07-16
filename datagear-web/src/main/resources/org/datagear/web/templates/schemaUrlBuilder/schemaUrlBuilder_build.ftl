<#--
 *
 * Copyright 2018 datagear.tech
 *
 * Licensed under the LGPLv3 license:
 * http://www.gnu.org/licenses/lgpl-3.0.html
 *
-->
<#include "../include/page_import.ftl">
<#include "../include/html_doctype.ftl">
<html>
<head>
<#include "../include/html_head.ftl">
<title>
	<#include "../include/html_app_name_prefix.ftl">
	<@spring.message code='module.schemaUrlBuilder' />
	<#include "../include/html_request_action_suffix.ftl">
</title>
</head>
<body class="p-card no-border">
<#include "../include/page_obj.ftl">
<div id="${pid}" class="page page-form horizontal">
	<div class="customScriptCode hidden">
		${scriptCode!''}
	</div>
	<div class="builtInJson hidden">
		${builtInBuildersJson!''}
	</div>
	<form class="flex flex-column" :class="{readonly: isReadonlyAction}">
		<div class="page-form-content flex-grow-1 pr-2 py-1 overflow-y-auto">
			<div class="field grid">
				<label for="${pid}dbType" class="field-label col-12 mb-2 md:col-3 md:mb-0">
					<@spring.message code='dbType' />
				</label>
		        <div class="field-input col-12 md:col-9">
		        	<p-dropdown v-model="pm.dbType" :options="dbTypeDropdownItems" option-label="dbType" option-value="dbType"
		        		option-group-label="label" option-group-children="items"  class="input w-full">
		        	</p-dropdown>
		        	<div class="validate-msg">
		        		<input name="dbType" required type="text" class="validate-proxy" />
		        	</div>
		        </div>
			</div>
			<div class="field grid">
				<label for="${pid}host" class="field-label col-12 mb-2 md:col-3 md:mb-0">
					<@spring.message code='hostNameOrIp' />
				</label>
		        <div class="field-input col-12 md:col-9">
		        	<p-inputtext id="${pid}host" v-model="pm.host" type="text" class="input w-full"
		        		name="host" maxlength="200">
		        	</p-inputtext>
		        </div>
			</div>
			<div class="field grid">
				<label for="${pid}port" class="field-label col-12 mb-2 md:col-3 md:mb-0">
					<@spring.message code='port' />
				</label>
		        <div class="field-input col-12 md:col-9">
		        	<p-inputtext id="${pid}port" v-model="pm.port" type="text" class="input w-full"
		        		name="port" maxlength="20">
		        	</p-inputtext>
		        </div>
			</div>
			<div class="field grid">
				<label for="${pid}name" class="field-label col-12 mb-2 md:col-3 md:mb-0">
					<@spring.message code='schemaName' />
				</label>
		        <div class="field-input col-12 md:col-9">
		        	<p-inputtext id="${pid}name" v-model="pm.name" type="text" class="input w-full"
		        		name="name" maxlength="500">
		        	</p-inputtext>
		        </div>
			</div>
		</div>
		<div class="page-form-foot flex-grow-0 pt-3 text-center">
			<p-button type="submit" label="<@spring.message code='confirm' />"></p-button>
		</div>
	</form>
</div>
<#include "../include/page_form.ftl">
<script>
(function(po)
{
	po.initUrl = "${(url!'')?js_string?no_esc}";
	
	$.schemaUrlBuilder.clear();
	
	var topBuilders = [];
	var allBuilders = [];
	
	try
	{
		var scriptCode = po.element(".customScriptCode").text();
		var builtInBuildersJson = po.element(".builtInJson").text();
		
		if(builtInBuildersJson)
			allBuilders = eval("$.schemaUrlBuilder.add(" + builtInBuildersJson+");");
		
		if(scriptCode)
			topBuilders = eval("$.schemaUrlBuilder.add(" + scriptCode+");");
	}
	catch(e){}
	
	if(topBuilders.length <= 0)
		topBuilders = (allBuilders.length <= 3 ? allBuilders : allBuilders.slice(0, 3));
	
	var urlDbType = null;
	var urlValue = null;
	
	if(po.initUrl)
	{
		var urlInfo = $.schemaUrlBuilder.extract(po.initUrl);
		if(urlInfo != null)
		{
			urlDbType = urlInfo.dbType;
			urlValue = urlInfo.value;
		}
	}
	
	if(!urlValue)
		urlValue = $.schemaUrlBuilder.defaultValue(urlDbType);
	
	po.submitForm = function()
	{
		var pm = po.vuePageModel();
		var url = $.schemaUrlBuilder.build(pm.dbType, pm);
		
		po.defaultSubmitSuccessCallback({ data: url });
	};
	
	var formModel =
	{
		dbType: urlDbType,
		host: (urlValue && urlValue.host ? urlValue.host : ""),
		port: (urlValue && urlValue.port ? urlValue.port : ""),
		name: (urlValue && urlValue.name ? urlValue.name : "")
	};
	
	po.setupForm(formModel, "#");
	
	po.vueRef("dbTypeDropdownItems",
	[
		{
			label: "<@spring.message code='common' />",
			items: topBuilders
		},
		{
			label: "<@spring.message code='all' />",
			items: allBuilders
		}
	]);
	
	po.vueMount();
})
(${pid});
</script>
</body>
</html>