/*
 * Copyright 2024 Johns Hopkins University
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
import { fixture, test, Selector } from 'testcafe';
import fs from 'fs';

fixture('NIHMS API Token Refresh')
    .skipJsErrors();

test('Get New NIHMS API Token', async t => {
    const nihmsUser = process.env.NIHMS_USER;
    const nihmsPassword = process.env.NIHMS_PASSWORD;

    await t
        .navigateTo('https://www.ncbi.nlm.nih.gov/pmc/utils/pacm/login')
        .setTestSpeed(0.5)
        .switchToIframe('#loginframe')
        .click(Selector('#era'))
        .switchToMainWindow()
        .typeText('#USER', nihmsUser)
        .typeText('#PASSWORD', nihmsPassword)
        .click('form.nih-login-form button.nih-white-button')
        .click(Selector('a').withText('API Token'));

    const sectionContent = await Selector('div.section-content').textContent;
    const partsContent = sectionContent.split('&api-token=');
    if (partsContent.length < 2) {
        throw new Error('Unable to find api-token in: ' + sectionContent);
    }
    const token = partsContent[1];
    const nihmsOutFile = process.env.NIHMS_OUTFILE;
    fs.writeFileSync(nihmsOutFile, token);
});