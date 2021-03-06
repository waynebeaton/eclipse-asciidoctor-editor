/*
 * Copyright 2018 Albert Tregnaghi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 */
package de.jcup.asciidoctoreditor;

import static de.jcup.asciidoctoreditor.EclipseUtil.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.IProgressMonitor;

class WaitForGeneratedFileAndShowInsideIternalPreviewRunner implements EnsureFileRunnable {

	private final AsciiDoctorEditor asciiDoctorEditor;
	private IProgressMonitor monitor;

	WaitForGeneratedFileAndShowInsideIternalPreviewRunner(AsciiDoctorEditor asciiDoctorEditor, IProgressMonitor monitor) {
		this.asciiDoctorEditor = asciiDoctorEditor;
		this.monitor = monitor;
	}

	@Override
	public void run() {
		long start = System.currentTimeMillis();
		boolean aquired = false;
		try {
			while (this.asciiDoctorEditor.isNotCanceled(monitor)
					&& (this.asciiDoctorEditor.temporaryInternalPreviewFile == null || !this.asciiDoctorEditor.temporaryInternalPreviewFile.exists())) {
				if (System.currentTimeMillis() - start > 20000) {
					// after 20 seconds there seems to be no chance to get
					// the generated preview file back
					this.asciiDoctorEditor.browserAccess.safeBrowserSetText(
							"<html><body><h3>Preview file generation timed out, so preview not available.</h3></body></html>");
					return;
				}
				Thread.sleep(300);
			}
			aquired = this.asciiDoctorEditor.outputBuildSemaphore.tryAcquire(5, TimeUnit.SECONDS);

			safeAsyncExec(() -> {

				try {
					URL url = this.asciiDoctorEditor.temporaryInternalPreviewFile.toURI().toURL();
					String foundURL = this.asciiDoctorEditor.browserAccess.getUrl();
					try {
						URL formerURL = new URL(this.asciiDoctorEditor.browserAccess.getUrl());
						foundURL = formerURL.toExternalForm();
					} catch (MalformedURLException e) {
						/* ignore - about pages etc. */
					}
					String externalForm = url.toExternalForm();
					if (!externalForm.equals(foundURL)) {
						this.asciiDoctorEditor.browserAccess.setUrl(externalForm);
					} else {
						this.asciiDoctorEditor.browserAccess.refresh();
					}

				} catch (MalformedURLException e) {
					AsciiDoctorEditorUtil.logError("Was not able to use malformed URL", e);
					this.asciiDoctorEditor.browserAccess.safeBrowserSetText("<html><body><h3>URL malformed</h3></body></html>");
				}
			});

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			if (aquired == true) {
				this.asciiDoctorEditor.outputBuildSemaphore.release();
			}
		}

	}
}