package com.ikokoon.serenity.model;

import java.io.Serializable;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

import com.ikokoon.toolkit.Toolkit;

/**
 * @author Michael Couck
 * @since 12.08.09
 * @version 01.00
 */
@Unique(fields = { Composite.NAME })
public class Package<E, F> extends Composite<Project<?, ?>, Class<?, ?>> implements Comparable<Package<?, ?>>, Serializable {

	private String name;

	@Legend(name = COVERAGE, limits = { COVERAGE_GOOD, COVERAGE_OK, COVERAGE_BAD }, positive = 1.0)
	private double coverage;
	@Legend(name = COMPLEXITY, limits = { COMPLEXITY_GOOD, COMPLEXITY_OK, COMPLEXITY_BAD })
	private double complexity;
	@Legend(name = ABSTRACTNESS, limits = { ABSTRACTNESS_GOOD, ABSTRACTNESS_OK, ABSTRACTNESS_BAD }, positive = 1.0)
	private double abstractness;
	@Legend(name = STABILITY, limits = { STABILITY_GOOD, STABILITY_OK, STABILITY_BAD }, positive = 1.0)
	private double stability;
	@Legend(name = DISTANCE, limits = { DISTANCE_GOOD, DISTANCE_OK, DISTANCE_BAD }, positive = 1.0)
	private double distance;
	@Legend(name = LINES, limits = { NO_LIMIT, NO_LIMIT, NO_LIMIT })
	private double lines;
	@Legend(name = INTERFACES, limits = { NO_LIMIT, NO_LIMIT, NO_LIMIT })
	private double interfaces;
	@Legend(name = IMPLEMENTATIONS, limits = { NO_LIMIT, NO_LIMIT, NO_LIMIT })
	private double implement;
	@Legend(name = EXECUTED, limits = { NO_LIMIT, NO_LIMIT, NO_LIMIT })
	private double executed;

	private double efferent;
	private double afferent;
	private Date timestamp;
	private Set<Efferent> efference = new TreeSet<Efferent>();
	private Set<Afferent> afference = new TreeSet<Afferent>();

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public double getLines() {
		return lines;
	}

	public void setLines(double lines) {
		this.lines = lines;
	}

	public double getExecuted() {
		return executed;
	}

	public void setExecuted(double totalLinesExecuted) {
		this.executed = totalLinesExecuted;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}

	public double getComplexity() {
		return Toolkit.format(complexity, PRECISION);
	}

	public void setComplexity(double complexity) {
		this.complexity = complexity;
	}

	public double getCoverage() {
		return Toolkit.format(coverage, PRECISION);
	}

	public void setCoverage(double coverage) {
		this.coverage = coverage;
	}

	public double getAbstractness() {
		return Toolkit.format(abstractness, PRECISION);
	}

	public void setAbstractness(double abstractness) {
		this.abstractness = abstractness;
	}

	public double getStability() {
		return Toolkit.format(stability, PRECISION);
	}

	public void setStability(double stability) {
		this.stability = stability;
	}

	public double getDistance() {
		return Toolkit.format(distance, PRECISION);
	}

	public void setDistance(double distance) {
		this.distance = distance;
	}

	public double getInterfaces() {
		return Toolkit.format(interfaces, PRECISION);
	}

	public void setInterfaces(double interfaces) {
		this.interfaces = interfaces;
	}

	public double getImplement() {
		return Toolkit.format(implement, PRECISION);
	}

	public void setImplement(double implementations) {
		this.implement = implementations;
	}

	public double getEfferent() {
		return Toolkit.format(efferent, PRECISION);
	}

	public void setEfferent(double efferent) {
		this.efferent = efferent;
	}

	public double getAfferent() {
		return Toolkit.format(afferent, PRECISION);
	}

	public void setAfferent(double afferent) {
		this.afferent = afferent;
	}

	public Set<Efferent> getEfference() {
		return efference;
	}

	public void setEfference(Set<Efferent> efference) {
		this.efference = efference;
	}

	public Set<Afferent> getAfference() {
		return afference;
	}

	public void setAfference(Set<Afferent> afference) {
		this.afference = afference;
	}

	public String toString() {
		return getId() + ":" + name;
	}

	public int compareTo(Package<?, ?> o) {
		int comparison = 0;
		if (this.getId() != null && o.getId() != null) {
			comparison = this.getId().compareTo(o.getId());
		} else {
			if (this.getName() != null && o.getName() != null) {
				comparison = this.getName().compareTo(o.getName());
			}
		}
		return comparison;
	}

}