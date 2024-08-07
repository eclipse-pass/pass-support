# NIHMS API Token Refresher

The NIHMS harvester process requires an Authentication token.  This token is available from the PACM utils page and is
valid for three months.  There is currently no API available to refresh the token.  This small TestCafe script logs
into the PACM utils page using th ERA Commons login option, clicks on the API Token link, and writes the new token
to a file named in the `NIHMS_OUTFILE` environment variable.

In order to run the token refresher, do the following:

- Install NodeJS version 20 or higher: https://nodejs.org/en/learn/getting-started/how-to-install-nodejs

- Set the following environment variables:

`NIHMS_USER` : The ERA Commons login username  
`NIHMS_PASSWORD` : The ERA Commons login password  
`NIHMS_OUTFILE` : The full path to the file to write the new token

- Then run the following:

`npm install`  
`npm run refresh-token`  


