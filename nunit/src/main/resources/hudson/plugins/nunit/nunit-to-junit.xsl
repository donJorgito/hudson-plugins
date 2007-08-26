<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="xml" indent="yes" />
	<xsl:template match="/test-results">

	<testsuites>
        <xsl:for-each select="test-suite//results//test-case[1]" >
			<xsl:for-each select="../.." >
				<xsl:variable name="firstTestName" select="results//test-case[1]//@name"/>
				<xsl:variable name="assembly" select="concat(substring-before($firstTestName, @name), @name)"/>

				<testsuite name="{$assembly}" tests="{count(*/test-case)}" time="{@time}"
					failures="{count(*/test-case/failure)}" errors="0">
				<xsl:for-each select="*/test-case">
					<testcase classname="{$assembly}" name="{substring-after(./@name, concat($assembly,'.'))}" time="{@time}">
					
					<xsl:variable name="generalfailure" select="./failure"/>
					
					<xsl:if test="./failure">
					<xsl:variable name="failstack" select="count(./failure/stack-trace/*) + count(./failure/stack-trace/text())"/>
						<failure>
						<xsl:choose>
							<xsl:when test="$failstack &gt; 0 or not($generalfailure)">
MESSAGE: <xsl:value-of select="./failure/message"/>
+++++++++++++++++++
STACK TRACE: <xsl:value-of select="./failure/stack-trace"/>
							</xsl:when>
							<xsl:otherwise>
MESSAGE: <xsl:value-of select="$generalfailure/message"/>
+++++++++++++++++++
STACK TRACE: <xsl:value-of select="$generalfailure/stack-trace"/>
							</xsl:otherwise>
						</xsl:choose>
						</failure>
					</xsl:if>
					
					
					</testcase>
				</xsl:for-each>
				</testsuite>
			</xsl:for-each>
			<!--
			<xsl:variable name="testcases" select="@name"/>
			<a name="{$testcases}"/>
		-->
        </xsl:for-each>

	</testsuites>
	</xsl:template>
</xsl:stylesheet>	