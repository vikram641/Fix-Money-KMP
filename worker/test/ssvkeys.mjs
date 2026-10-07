import { generateKeyPairSync, sign } from "node:crypto";
import { writeFileSync, readFileSync, existsSync } from "node:fs";
const dir = process.argv[2], mode = process.argv[3];
if (mode === "gen") {
  const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "P-256" });
  writeFileSync(dir + "/priv.pem", privateKey.export({ type: "pkcs8", format: "pem" }));
  console.log(publicKey.export({ type: "spki", format: "der" }).toString("base64"));
} else {
  // mode = "sign", args: userId txId
  const [userId, tx] = process.argv.slice(4);
  const content = `ad_network=5450213213286189855&ad_unit=1234567890&custom_data=x&reward_amount=5&reward_item=coins&timestamp=${Date.now()}&transaction_id=${tx}&user_id=${userId}`;
  const sig = sign("sha256", Buffer.from(content), readFileSync(dir + "/priv.pem"));
  console.log(`${content}&signature=${sig.toString("base64url")}&key_id=test`);
}
